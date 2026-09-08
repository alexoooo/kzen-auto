package tech.kzen.auto.common.data.catalog

interface CatalogSource {
    suspend fun catalog(refresh: Boolean): CatalogSnapshot
    fun prepare(entries: List<String>)
    fun cancel(entries: List<String>)
}
