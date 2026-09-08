package tech.kzen.auto.common.data.catalog

import kotlinx.serialization.Serializable

@Serializable
data class CatalogEntry(
    val id: String,
    val date: String,
    val fileName: String,
    val sourceUrl: String,
    val sizeBytes: Long,
    val downloaded: Boolean,
    val state: String,
    val completedBytes: Long,
    val detail: String,
    val symbols: List<String>
)
