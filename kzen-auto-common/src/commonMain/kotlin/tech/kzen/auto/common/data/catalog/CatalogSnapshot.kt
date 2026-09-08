package tech.kzen.auto.common.data.catalog

import kotlinx.serialization.Serializable

@Serializable
data class CatalogSnapshot(val entries: List<CatalogEntry>, val error: String?)
