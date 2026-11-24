package com.essvocab.app.data.sync

import com.essvocab.app.data.local.VocabDao
import com.essvocab.app.data.local.VocabPackEntity
import com.essvocab.app.data.local.VocabWordEntity
import com.essvocab.app.data.preferences.AppPreferencesRepository
import com.essvocab.app.data.preferences.AppSettings
import com.essvocab.app.data.remote.RemoteManifestDto
import com.essvocab.app.data.remote.RemotePackPayloadDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

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
     * When using the official GitHub repo source, races multiple China-friendly CDN mirrors
     * concurrently so the fastest reachable node wins.
     */
    suspend fun sync(forceFullCheck: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        if (!syncStateStore.isOnlineSyncConsentGranted()) {
            return@withContext SyncResult.ConsentRequired
        }

        val configuredUrlStr = AppSettings.normalizeManifestUrl(syncStateStore.getManifestUrl())
        val candidateUrls = if (configuredUrlStr in AppSettings.OFFICIAL_MIRROR_URLS) {
            buildList {
                add(configuredUrlStr)
                AppSettings.OFFICIAL_MIRROR_URLS.forEach { mirror ->
                    if (mirror != configuredUrlStr) add(mirror)
                }
            }
        } else {
            listOf(configuredUrlStr)
        }

        syncFromCandidates(candidateUrls, forceFullCheck)
    }

    internal suspend fun syncFromCandidates(
        candidateUrls: List<String>,
        forceFullCheck: Boolean,
    ): SyncResult = withContext(Dispatchers.IO) {
        if (!syncStateStore.isOnlineSyncConsentGranted()) {
            return@withContext SyncResult.ConsentRequired
        }
        if (candidateUrls.isEmpty()) {
            return@withContext SyncResult.Error("未配置有效的远程词库地址")
        }
        if (candidateUrls.size == 1) {
            return@withContext syncFromSingleUrl(candidateUrls.first(), forceFullCheck)
        }
        syncWithMirrorRace(candidateUrls, forceFullCheck)
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

        return applyManifestFromMirror(
            manifestUrlStr = manifestUrlStr,
            manifestDto = manifestDto,
            newEtag = newEtag,
            localPacksBefore = localPacksBefore,
            forceFullCheck = forceFullCheck,
        )
    }

    private suspend fun syncWithMirrorRace(
        candidateUrls: List<String>,
        forceFullCheck: Boolean,
    ): SyncResult = supervisorScope {
        val localPacksBefore = vocabDao.getAllPacks().associateBy { it.id }
        val previousEtag = if (forceFullCheck || localPacksBefore.isEmpty()) "" else syncStateStore.getManifestEtag()

        val channel = Channel<MirrorManifestAttempt>(Channel.BUFFERED)
        val probeJobs = candidateUrls.map { mirrorUrlStr ->
            launch(Dispatchers.IO) {
                val httpUrl = mirrorUrlStr.toHttpUrlOrNull()
                val fetchResult = if (httpUrl == null) {
                    ManifestFetchResult.Failure("词库地址格式无效：$mirrorUrlStr")
                } else {
                    fetchManifestCancellable(httpUrl, previousEtag)
                }
                channel.trySend(MirrorManifestAttempt(mirrorUrlStr, fetchResult))
            }
        }

        var lastError: SyncResult.Error? = null
        try {
            repeat(candidateUrls.size) {
                val attempt = channel.receive()
                when (val fetched = attempt.result) {
                    is ManifestFetchResult.NotModified -> {
                        probeJobs.forEach { it.cancel() }
                        val count = vocabDao.countActiveWords()
                        return@supervisorScope SyncResult.UpToDate(datasetVersion = "", totalWords = count)
                    }

                    is ManifestFetchResult.Success -> {
                        val applied = applyManifestFromMirror(
                            manifestUrlStr = attempt.mirrorUrlStr,
                            manifestDto = fetched.dto,
                            newEtag = fetched.etag,
                            localPacksBefore = localPacksBefore,
                            forceFullCheck = forceFullCheck,
                        )
                        when (applied) {
                            is SyncResult.Updated,
                            is SyncResult.UpToDate,
                            is SyncResult.ConsentRequired,
                            -> {
                                probeJobs.forEach { it.cancel() }
                                return@supervisorScope applied
                            }

                            is SyncResult.Error -> lastError = applied
                        }
                    }

                    is ManifestFetchResult.Failure -> {
                        lastError = SyncResult.Error(fetched.message)
                    }
                }
            }
        } finally {
            probeJobs.forEach { it.cancel() }
            channel.close()
        }

        SyncResult.Error("已自动尝试多个加速节点仍无法连接，请检查手机网络后重试（${lastError?.message ?: "网络超时"}）")
    }

    private suspend fun applyManifestFromMirror(
        manifestUrlStr: String,
        manifestDto: RemoteManifestDto,
        newEtag: String?,
        localPacksBefore: Map<String, VocabPackEntity>,
        forceFullCheck: Boolean,
    ): SyncResult {
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

            val packUrl = resolvePackUrl(manifestUrlStr, packMeta.path)
                ?: return SyncResult.Error("无法解析分类包地址：${packMeta.path}")

            val packRequest = Request.Builder()
                .url(packUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val packBytes = try {
                okHttpClient.newCall(packRequest).execute().use { resp ->
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

    private fun buildManifestRequest(manifestHttpUrl: HttpUrl, previousEtag: String): Request {
        return Request.Builder()
            .url(manifestHttpUrl)
            .header("User-Agent", USER_AGENT)
            .apply {
                if (previousEtag.isNotBlank()) {
                    header("If-None-Match", previousEtag)
                }
            }
            .get()
            .build()
    }

    private fun parseManifestResponse(response: Response): ManifestFetchResult {
        if (response.code == 304) {
            return ManifestFetchResult.NotModified
        }
        if (!response.isSuccessful) {
            return ManifestFetchResult.Failure("拉取词库清单失败（HTTP ${response.code}）")
        }
        val bodyStr = response.body?.string()
            ?: return ManifestFetchResult.Failure("词库清单响应内容为空")
        return try {
            val parsed = json.decodeFromString<RemoteManifestDto>(bodyStr)
            ManifestFetchResult.Success(parsed, response.header("ETag"))
        } catch (e: Exception) {
            ManifestFetchResult.Failure("词库清单解析失败：${e.message ?: "JSON 格式错误"}")
        }
    }

    private fun fetchManifest(manifestHttpUrl: HttpUrl, previousEtag: String): ManifestFetchResult {
        val request = buildManifestRequest(manifestHttpUrl, previousEtag)
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                parseManifestResponse(response)
            }
        } catch (e: IOException) {
            ManifestFetchResult.Failure("网络连接失败：${e.message ?: "请检查网络或词库源地址"}")
        } catch (e: Exception) {
            ManifestFetchResult.Failure("词库清单解析失败：${e.message ?: "JSON 格式错误"}")
        }
    }

    private suspend fun fetchManifestCancellable(
        manifestHttpUrl: HttpUrl,
        previousEtag: String,
    ): ManifestFetchResult = suspendCancellableCoroutine { cont ->
        val call = okHttpClient.newCall(buildManifestRequest(manifestHttpUrl, previousEtag))
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resume(
                            ManifestFetchResult.Failure(
                                "网络连接失败：${e.message ?: "请检查网络或词库源地址"}",
                            ),
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { parseManifestResponse(it) }
                    } catch (e: Exception) {
                        ManifestFetchResult.Failure("词库清单解析失败：${e.message ?: "JSON 格式错误"}")
                    }
                    if (cont.isActive) {
                        cont.resume(result)
                    }
                }
            },
        )
    }

    suspend fun testConnection(url: String): SyncResult = withContext(Dispatchers.IO) {
        if (!syncStateStore.isOnlineSyncConsentGranted()) {
            return@withContext SyncResult.ConsentRequired
        }
        val httpUrl = url.trim().toHttpUrlOrNull()
            ?: return@withContext SyncResult.Error("URL 格式不正确")
        try {
            val req = Request.Builder().url(httpUrl).header("User-Agent", USER_AGENT).get().build()
            okHttpClient.newCall(req).execute().use { resp ->
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

    private data class MirrorManifestAttempt(
        val mirrorUrlStr: String,
        val result: ManifestFetchResult,
    )

    private sealed interface ManifestFetchResult {
        data object NotModified : ManifestFetchResult
        data class Success(val dto: RemoteManifestDto, val etag: String?) : ManifestFetchResult
        data class Failure(val message: String) : ManifestFetchResult
    }

    companion object {
        private const val USER_AGENT = "EssVocab-Android/1.0"

        fun resolvePackUrl(manifestUrlStr: String, packPath: String): HttpUrl? {
            val trimmedPath = packPath.trim()
            if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
                return trimmedPath.toHttpUrlOrNull()
            }
            val baseDir = manifestUrlStr.trim().substringBefore('?').substringBeforeLast('/')
            return "$baseDir/${trimmedPath.trimStart('/')}".toHttpUrlOrNull()
        }

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
