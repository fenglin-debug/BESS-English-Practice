package com.essvocab.app.data.sync

import com.essvocab.app.data.local.PackSummaryRow
import com.essvocab.app.data.local.VocabDao
import com.essvocab.app.data.local.VocabPackEntity
import com.essvocab.app.data.local.VocabWordEntity
import com.essvocab.app.data.local.WordWithReview
import com.essvocab.app.data.preferences.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VocabSyncRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var vocabDao: InMemoryVocabDao
    private lateinit var stateStore: FakeSyncStateStore
    private lateinit var repository: VocabSyncRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        vocabDao = InMemoryVocabDao()
        stateStore = FakeSyncStateStore(
            url = server.url("/manifest.json").toString(),
            consentGranted = true,
        )
        repository = VocabSyncRepository(
            okHttpClient = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            vocabDao = vocabDao,
            syncStateStore = stateStore,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun syncAndTestConnectionRefuseNetworkRequestsWithoutUserConsent() = runTest {
        stateStore.consentGranted = false

        val syncRes = repository.sync(forceFullCheck = true)
        val testRes = repository.testConnection(server.url("/manifest.json").toString())

        assertTrue(syncRes is SyncResult.ConsentRequired)
        assertTrue(testRes is SyncResult.ConsentRequired)
        assertEquals("No HTTP request may be sent before user grants consent", 0, server.requestCount)
        assertEquals(0, vocabDao.countActiveWords())
    }

    @Test
    fun syncDownloadsPacksHandlesEtag304AndHotUpdatesIncrementally() = runTest {
        val packV1 = """
            {
              "packId": "basic_system",
              "packName": "基础术语与系统",
              "description": "储能基础术语",
              "version": 1,
              "words": [
                {
                  "id": "ess_bess_001",
                  "seq": 1,
                  "zh": "电池储能系统",
                  "en": "Battery energy storage system (BESS)",
                  "ttsText": "Battery energy storage system",
                  "abbreviations": ["BESS"],
                  "ipa": "ˈbætəri ˈɛnərʤi ˈstɔrɪʤ ˈsɪstəm"
                },
                {
                  "id": "ess_old_999",
                  "seq": 2,
                  "zh": "旧词条",
                  "en": "Legacy term",
                  "ttsText": "Legacy term",
                  "abbreviations": [],
                  "ipa": ""
                }
              ]
            }
        """.trimIndent()
        val packV1Hash = VocabSyncRepository.sha256Hex(packV1.toByteArray())

        val manifestV1 = """
            {
              "schemaVersion": 1,
              "datasetVersion": "2026.11.23.1",
              "totalWords": 2,
              "packs": [
                {
                  "id": "basic_system",
                  "name": "基础术语与系统",
                  "order": 1,
                  "wordCount": 2,
                  "version": 1,
                  "sha256": "$packV1Hash",
                  "path": "packs/basic_system.json"
                }
              ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(manifestV1).addHeader("ETag", "\"v1\""))
        server.enqueue(MockResponse().setBody(packV1))

        val firstRes = repository.sync()
        assertTrue(firstRes is SyncResult.Updated)
        assertEquals(2, (firstRes as SyncResult.Updated).totalWords)
        assertEquals("\"v1\"", stateStore.etag)
        assertEquals("2026.11.23.1", stateStore.version)

        // Second check returns 304 Not Modified (sends If-None-Match header)
        server.enqueue(MockResponse().setResponseCode(304))
        val secondRes = repository.sync()
        assertTrue(secondRes is SyncResult.UpToDate)
        server.takeRequest() // manifest v1
        server.takeRequest() // pack v1
        val manifestReq304 = server.takeRequest()
        assertEquals("\"v1\"", manifestReq304.getHeader("If-None-Match"))

        // Remote publishes v2: updates zh for ess_bess_001, removes ess_old_999, adds ess_pcs_002
        val packV2 = """
            {
              "packId": "basic_system",
              "packName": "基础术语与系统",
              "description": "储能基础术语",
              "version": 2,
              "words": [
                {
                  "id": "ess_bess_001",
                  "seq": 1,
                  "zh": "电池储能系统（修订版）",
                  "en": "Battery energy storage system (BESS)",
                  "ttsText": "Battery energy storage system",
                  "abbreviations": ["BESS"],
                  "ipa": "ˈbætəri ˈɛnərʤi ˈstɔrɪʤ ˈsɪstəm"
                },
                {
                  "id": "ess_pcs_002",
                  "seq": 2,
                  "zh": "储能变流器",
                  "en": "Power conversion system (PCS)",
                  "ttsText": "Power conversion system",
                  "abbreviations": ["PCS"],
                  "ipa": "paʊər kənˈvɜːrʒən ˈsɪstəm"
                }
              ]
            }
        """.trimIndent()
        val packV2Hash = VocabSyncRepository.sha256Hex(packV2.toByteArray())
        val manifestV2 = """
            {
              "schemaVersion": 1,
              "datasetVersion": "2026.11.23.2",
              "totalWords": 2,
              "packs": [
                {
                  "id": "basic_system",
                  "name": "基础术语与系统",
                  "order": 1,
                  "wordCount": 2,
                  "version": 2,
                  "sha256": "$packV2Hash",
                  "path": "packs/basic_system.json"
                }
              ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(manifestV2).addHeader("ETag", "\"v2\""))
        server.enqueue(MockResponse().setBody(packV2))

        val thirdRes = repository.sync()
        assertTrue(thirdRes is SyncResult.Updated)
        assertEquals(2, (thirdRes as SyncResult.Updated).totalWords)
        assertEquals("电池储能系统（修订版）", vocabDao.words["ess_bess_001"]?.zh)
        assertEquals("储能变流器", vocabDao.words["ess_pcs_002"]?.zh)
        assertFalse(vocabDao.words["ess_old_999"]!!.isActive)
        assertEquals(2, vocabDao.countActiveWords())
    }

    @Test
    fun syncRejectsCorruptedPackWithSha256Mismatch() = runTest {
        val manifest = """
            {
              "schemaVersion": 1,
              "datasetVersion": "2026.11.23.1",
              "totalWords": 1,
              "packs": [
                {
                  "id": "basic_system",
                  "name": "基础术语与系统",
                  "order": 1,
                  "wordCount": 1,
                  "version": 1,
                  "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
                  "path": "packs/basic_system.json"
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(manifest))
        server.enqueue(MockResponse().setBody("""{"packId":"basic_system","packName":"x","words":[]}"""))

        val res = repository.sync()
        assertTrue(res is SyncResult.Error)
        assertTrue((res as SyncResult.Error).message.contains("SHA-256"))
        assertEquals(0, vocabDao.countActiveWords())
    }

    @Test
    fun normalizeManifestUrlRecoversFromGithubRepoUrlAndPlaceholders() {
        assertEquals(
            AppSettings.DEFAULT_MANIFEST_URL,
            AppSettings.normalizeManifestUrl("https://github.com/fenglin-debug/BESS-English-Practice.git"),
        )
        assertEquals(
            AppSettings.DEFAULT_MANIFEST_URL,
            AppSettings.normalizeManifestUrl("https://github.com/fenglin-debug/BESS-English-Practice"),
        )
        assertEquals(
            AppSettings.DEFAULT_MANIFEST_URL,
            AppSettings.normalizeManifestUrl("https://raw.githubusercontent.com/<YOUR_GITHUB_USER>/repo/main/manifest.json"),
        )
        assertEquals(
            AppSettings.DEFAULT_MANIFEST_URL,
            AppSettings.normalizeManifestUrl("   "),
        )
        assertEquals(
            "http://10.0.2.2:8000/manifest.json",
            AppSettings.normalizeManifestUrl("http://10.0.2.2:8000/manifest.json"),
        )
    }

    @Test
    fun resolvePackUrlPreservesProxyPrefixUrls() {
        val resolved = VocabSyncRepository.resolvePackUrl(
            "https://ghfast.top/https://raw.githubusercontent.com/fenglin-debug/BESS-English-Practice/main/vocab-repo/dist/manifest.json",
            "packs/basic_system.json",
        )
        assertEquals(
            "https://ghfast.top/https://raw.githubusercontent.com/fenglin-debug/BESS-English-Practice/main/vocab-repo/dist/packs/basic_system.json",
            resolved?.toString(),
        )
    }

    @Test
    fun syncMirrorRaceSucceedsWhenPrimaryMirrorFails() = runTest {
        val mirror2 = MockWebServer()
        mirror2.start()
        try {
            val packJson = """
                {
                  "packId": "basic_system",
                  "packName": "基础术语与系统",
                  "description": "储能基础术语",
                  "version": 1,
                  "words": [
                    {
                      "id": "ess_bess_001",
                      "seq": 1,
                      "zh": "电池储能系统",
                      "en": "Battery energy storage system (BESS)",
                      "ttsText": "Battery energy storage system",
                      "abbreviations": ["BESS"],
                      "ipa": "ˈbætəri ˈɛnərʤi ˈstɔrɪʤ ˈsɪstəm"
                    }
                  ]
                }
            """.trimIndent()
            val packHash = VocabSyncRepository.sha256Hex(packJson.toByteArray())
            val manifestJson = """
                {
                  "schemaVersion": 1,
                  "datasetVersion": "2026.11.24.1",
                  "totalWords": 1,
                  "packs": [
                    {
                      "id": "basic_system",
                      "name": "基础术语与系统",
                      "order": 1,
                      "wordCount": 1,
                      "version": 1,
                      "sha256": "$packHash",
                      "path": "packs/basic_system.json"
                    }
                  ]
                }
            """.trimIndent()

            // Mirror 1 fails immediately with HTTP 500
            server.enqueue(MockResponse().setResponseCode(500))
            // Mirror 2 succeeds with valid manifest + pack
            mirror2.enqueue(MockResponse().setBody(manifestJson).addHeader("ETag", "\"m2-v1\""))
            mirror2.enqueue(MockResponse().setBody(packJson))

            val res = repository.syncFromCandidates(
                candidateUrls = listOf(
                    server.url("/manifest.json").toString(),
                    mirror2.url("/manifest.json").toString(),
                ),
                forceFullCheck = true,
            )

            assertTrue("Expected Updated but got $res", res is SyncResult.Updated)
            assertEquals(1, (res as SyncResult.Updated).totalWords)
            assertEquals("电池储能系统", vocabDao.words["ess_bess_001"]?.zh)
        } finally {
            mirror2.shutdown()
        }
    }

    private class FakeSyncStateStore(
        private var url: String,
        var consentGranted: Boolean = true,
    ) : SyncStateStore {
        var etag: String = ""
        var version: String = ""
        override suspend fun isOnlineSyncConsentGranted(): Boolean = consentGranted
        override suspend fun getManifestUrl(): String = url
        override suspend fun getManifestEtag(): String = etag
        override suspend fun saveSyncMetadata(datasetVersion: String, etag: String?, syncedAtMillis: Long) {
            version = datasetVersion
            if (!etag.isNullOrBlank()) this.etag = etag
        }
    }

    private class InMemoryVocabDao : VocabDao {
        val packs = linkedMapOf<String, VocabPackEntity>()
        val words = linkedMapOf<String, VocabWordEntity>()
        private val wordsState = MutableStateFlow<List<VocabWordEntity>>(emptyList())

        override fun observePackSummaries(nowMillis: Long): Flow<List<PackSummaryRow>> =
            wordsState.map { emptyList() }

        override suspend fun getAllPacks(): List<VocabPackEntity> = packs.values.toList()

        override suspend fun upsertPack(pack: VocabPackEntity) {
            packs[pack.id] = pack
        }

        override suspend fun upsertWords(words: List<VocabWordEntity>) {
            words.forEach { this.words[it.id] = it }
            wordsState.value = this.words.values.toList()
        }

        override suspend fun deactivateWordsNotIn(packId: String, activeWordIds: List<String>) {
            val activeSet = activeWordIds.toSet()
            words.entries.forEach { (id, w) ->
                if (w.packId == packId && id !in activeSet) {
                    words[id] = w.copy(isActive = false)
                }
            }
            wordsState.value = words.values.toList()
        }

        override suspend fun deactivateAllWordsInPack(packId: String) {
            words.entries.forEach { (id, w) ->
                if (w.packId == packId) {
                    words[id] = w.copy(isActive = false)
                }
            }
            wordsState.value = words.values.toList()
        }

        override suspend fun deletePacksNotIn(activePackIds: List<String>) {
            val activeSet = activePackIds.toSet()
            packs.keys.removeAll { it !in activeSet }
        }

        override suspend fun deactivateWordsOutsidePacks(activePackIds: List<String>) {
            val activeSet = activePackIds.toSet()
            words.entries.forEach { (id, w) ->
                if (w.packId !in activeSet) {
                    words[id] = w.copy(isActive = false)
                }
            }
            wordsState.value = words.values.toList()
        }

        override fun observeTotalActiveWords(): Flow<Int> =
            wordsState.map { list -> list.count { it.isActive } }

        override suspend fun countActiveWords(): Int = words.values.count { it.isActive }

        override fun observeWords(packId: String?, query: String): Flow<List<WordWithReview>> =
            wordsState.map { emptyList() }
    }
}
