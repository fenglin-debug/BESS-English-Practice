package com.essvocab.app.data.sync

import com.essvocab.app.data.local.VocabDao
import com.essvocab.app.data.local.VocabPackEntity
import com.essvocab.app.data.local.VocabWordEntity
import com.essvocab.app.data.preferences.AppPreferencesRepository
import com.essvocab.app.data.preferences.AppSettings
import com.essvocab.app.data.remote.RemoteManifestDto
import com.essvocab.app.data.remote.RemotePackPayloadDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

interface SyncStateStore {
    suspend fun isOnlineSyncConsentGranted(): Boolean
    suspend fun getManifestUrl(): String
    suspend fun getManifestEtag(): String
    suspend fun saveSyncMetadata(datasetVersion: String, etag: String?, syncedAtMillis: Long)
}

@Singleton
class PreferencesSyncStateStore @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
) : SyncStateStore {
    override suspend fun isOnlineSyncConsentGranted(): Boolean =
        preferencesRepository.getCurrentSettings().onlineSyncConsentGranted

    override suspend fun getManifestUrl(): String =
        preferencesRepository.getCurrentSettings().manifestUrl

    override suspend fun getManifestEtag(): String =
        preferencesRepository.getCurrentSettings().manifestEtag

    override suspend fun saveSyncMetadata(datasetVersion: String, etag: String?, syncedAtMillis: Long) {
        preferencesRepository.updateSyncMetadata(datasetVersion, etag, syncedAtMillis)
    }
}

sealed interface SyncResult {
    data object ConsentRequired : SyncResult

    data class Updated(
        val datasetVersion: String,
        val updatedPacks: Int,
        val totalPacks: Int,
        val totalWords: Int,
    ) : SyncResult

    data class UpToDate(
        val datasetVersion: String,
        val totalWords: Int,
    ) : SyncResult

    data class Error(val message: String) : SyncResult
}

@Singleton
class VocabSyncRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val vocabDao: VocabDao,
    private val syncStateStore: SyncStateStore,
) {
    /**
     * Fetches manifest.json from the configured remote source and incrementally downloads
     * only packs whose SHA-256 hash changed or are missing locally.
     * Enforces strict user consent before any HTTP request is issued.
     * Existing user FSRS progress in review_cards is NEVER overwritten or deleted.
     */
    suspend fun sync(forceFullCheck: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        if (!syncStateStore.isOnlineSyncConsentGranted()) {
            return@withContext SyncResult.ConsentRequired
        }

        val configuredUrlStr = syncStateStore.getManifestUrl().trim()
        if (configuredUrlStr.isBlank()) {
            return@withContext SyncResult.Error("请先在「设置」中配置有效的远程词库 manifest.json 地址")
        }

        val candidateUrls = buildList {
            add(configuredUrlStr)
            if (configuredUrlStr == AppSettings.DEFAULT_MANIFEST_URL) {
                add(AppSettings.GITHUB_RAW_MANIFEST_URL)
            }
        }

        var lastError: SyncResult.Error? = null
        for (urlStr in candidateUrls) {
            when (val res = syncFromSingleUrl(urlStr, forceFullCheck)) {
                is SyncResult.Updated, is SyncResult.UpToDate, is SyncResult.ConsentRequired -> return@withContext res
                is SyncResult.Error -> lastError = res
            }
        }
        lastError ?: SyncResult.Error("同步失败，请稍后重试")
    }

    private suspend fun syncFromSingleUrl(
        manifestUrlStr: String,
        forceFullCheck: Boolean,
    ): SyncResult {
        val manifestHttpUrl = manifestUrlStr.toHttpUrlOrNull()
            ?: return SyncResult.Error("词库地址格式无效：$manifestUrlStr")

        val localPacksBefore = vocabDao.getAllPacks().associateBy { it.id }
        val previousEtag = if (forceFullCheck || localPacksBefore.isEmpty()) "" else syncStateStore.getManifestEtag()

        val (manifestDto, newEtag) = when (val fetched = fetchManifest(manifestHttpUrl, previousEtag)) {
            is ManifestFetchResult.NotModified -> {
                val count = vocabDao.countActiveWords()
                return SyncResult.UpToDate(datasetVersion = "", totalWords = count)
            }

            is ManifestFetchResult.Success -> fetched.dto to fetched.etag
            is ManifestFetchResult.Failure -> return SyncResult.Error(fetched.message)
        }

        if (manifestDto.packs.isEmpty()) {
            return SyncResult.Error("远程清单未包含任何词库分类")
        }

        val nowMillis = System.currentTimeMillis()
        var updatedPackCount = 0

        for (packMeta in manifestDto.packs) {
            val localPack = localPacksBefore[packMeta.id]
            val needsDownload = forceFullCheck ||
                localPack == null ||
                !localPack.sha256.equals(packMeta.sha256, ignoreCase = true)

            if (!needsDownload) {
                val existingPack = localPack ?: continue
                if (existingPack.orderIndex != packMeta.order ||
                    existingPack.name != packMeta.name ||
                    existingPack.description != packMeta.description
                ) {
                    vocabDao.upsertPack(
                        existingPack.copy(
                            name = packMeta.name,
                            description = packMeta.description,
                            orderIndex = packMeta.order,
                        ),
                    )
                }
                continue
            }

            val packUrl = manifestHttpUrl.resolve(packMeta.path)
                ?: return SyncResult.Error("无法解析分类包地址：${packMeta.path}")

            val packBytes = try {
                okHttpClient.newCall(Request.Builder().url(packUrl).get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return SyncResult.Error(
                            "下载分类「${packMeta.name}」失败（HTTP ${resp.code}）",
                        )
                    }
                    resp.body?.bytes()
                        ?: return SyncResult.Error("分类「${packMeta.name}」返回内容为空")
                }
            } catch (e: IOException) {
                return SyncResult.Error("下载分类「${packMeta.name}」网络异常：${e.message}")
            }

            val actualHash = sha256Hex(packBytes)
            if (packMeta.sha256.isNotBlank() && !actualHash.equals(packMeta.sha256, ignoreCase = true)) {
                return SyncResult.Error(
                    "分类「${packMeta.name}」文件校验失败（SHA-256 不匹配），已取消写入以保护本地数据",
                )
            }

            val payload = try {
                json.decodeFromString<RemotePackPayloadDto>(packBytes.decodeToString())
            } catch (e: Exception) {
                return SyncResult.Error("分类「${packMeta.name}」JSON 解析失败：${e.message}")
            }

            val packEntity = VocabPackEntity(
                id = packMeta.id,
                name = packMeta.name,
                description = packMeta.description.ifBlank { payload.description },
                orderIndex = packMeta.order,
                wordCount = payload.words.size,
                version = packMeta.version,
                sha256 = actualHash,
                updatedAtMillis = nowMillis,
            )

            val wordEntities = payload.words.map { dto ->
                VocabWordEntity(
                    id = dto.id,
                    packId = packMeta.id,
                    seq = dto.seq,
                    zh = dto.zh,
                    en = dto.en,
                    ttsText = dto.ttsText?.takeIf { it.isNotBlank() } ?: dto.en,
                    abbreviationsCsv = dto.abbreviations.joinToString("|"),
                    ipa = dto.ipa,
                    exampleEn = dto.exampleEn,
                    exampleZh = dto.exampleZh,
                    note = dto.note,
                    audioUrl = dto.audioUrl,
                    isActive = true,
                )
            }

            vocabDao.replacePackWords(packEntity, wordEntities)
            updatedPackCount++
        }

        vocabDao.pruneRemovedPacks(manifestDto.packs.map { it.id })

        syncStateStore.saveSyncMetadata(
            datasetVersion = manifestDto.datasetVersion,
            etag = newEtag,
            syncedAtMillis = nowMillis,
        )

        val totalActive = vocabDao.countActiveWords()
        return if (updatedPackCount == 0 && localPacksBefore.size == manifestDto.packs.size) {
            SyncResult.UpToDate(
                datasetVersion = manifestDto.datasetVersion,
                totalWords = totalActive,
            )
        } else {
            SyncResult.Updated(
                datasetVersion = manifestDto.datasetVersion,
                updatedPacks = updatedPackCount,
                totalPacks = manifestDto.packs.size,
                totalWords = totalActive,
            )
        }
    }

    private fun fetchManifest(manifestHttpUrl: HttpUrl, previousEtag: String): ManifestFetchResult {
        val request = Request.Builder()
            .url(manifestHttpUrl)
            .apply {
                if (previousEtag.isNotBlank()) {
                    header("If-None-Match", previousEtag)
                }
            }
            .get()
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 304) {
                    return ManifestFetchResult.NotModified
                }
                if (!response.isSuccessful) {
                    return ManifestFetchResult.Failure("拉取词库清单失败（HTTP ${response.code}）")
                }
                val bodyStr = response.body?.string()
                    ?: return ManifestFetchResult.Failure("词库清单响应内容为空")
                val parsed = json.decodeFromString<RemoteManifestDto>(bodyStr)
                ManifestFetchResult.Success(parsed, response.header("ETag"))
            }
        } catch (e: IOException) {
            ManifestFetchResult.Failure("网络连接失败：${e.message ?: "请检查网络或词库源地址"}")
        } catch (e: Exception) {
            ManifestFetchResult.Failure("词库清单解析失败：${e.message ?: "JSON 格式错误"}")
        }
    }

    suspend fun testConnection(url: String): SyncResult = withContext(Dispatchers.IO) {
        if (!syncStateStore.isOnlineSyncConsentGranted()) {
            return@withContext SyncResult.ConsentRequired
        }
        val httpUrl = url.trim().toHttpUrlOrNull()
            ?: return@withContext SyncResult.Error("URL 格式不正确")
        try {
            okHttpClient.newCall(Request.Builder().url(httpUrl).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext SyncResult.Error("连接失败（HTTP ${resp.code}）")
                }
                val body = resp.body?.string() ?: return@withContext SyncResult.Error("响应内容为空")
                val manifest = json.decodeFromString<RemoteManifestDto>(body)
                SyncResult.UpToDate(
                    datasetVersion = manifest.datasetVersion,
                    totalWords = manifest.totalWords.takeIf { it > 0 }
                        ?: manifest.packs.sumOf { it.wordCount },
                )
            }
        } catch (e: Exception) {
            SyncResult.Error("连接失败：${e.message ?: "请检查地址与网络"}")
        }
    }

    private sealed interface ManifestFetchResult {
        data object NotModified : ManifestFetchResult
        data class Success(val dto: RemoteManifestDto, val etag: String?) : ManifestFetchResult
        data class Failure(val message: String) : ManifestFetchResult
    }

    companion object {
        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
