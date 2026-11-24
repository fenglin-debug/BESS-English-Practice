package com.essvocab.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class RemoteManifestDto(
    val schemaVersion: Int = 1,
    val datasetVersion: String,
    val updatedAt: String = "",
    val minAppVersionCode: Int = 1,
    val totalWords: Int = 0,
    val packs: List<RemotePackMetaDto>,
)

@Serializable
data class RemotePackMetaDto(
    val id: String,
    val name: String,
    val description: String = "",
    val order: Int = 0,
    val wordCount: Int = 0,
    val version: Int = 1,
    val sha256: String,
    val path: String,
)

@Serializable
data class RemotePackPayloadDto(
    val packId: String,
    val packName: String,
    val description: String = "",
    val version: Int = 1,
    val words: List<RemoteWordDto>,
)

@Serializable
data class RemoteWordDto(
    val id: String,
    val seq: Int = 0,
    val zh: String,
    val en: String,
    val ttsText: String? = null,
    val abbreviations: List<String> = emptyList(),
    val ipa: String = "",
    val exampleEn: String? = null,
    val exampleZh: String? = null,
    val note: String? = null,
    val audioUrl: String? = null,
)
