package tech.kzen.auto.server.data.content.provider

import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataSourceId
import tech.kzen.auto.server.data.content.ContentSourceException


class DataContentProviderLookup(
    private val local: DataContentProvider,
    providers: Map<DataSourceId, DataContentProvider>
) {
    private val providers = providers.toMap()


    fun get(ref: DataRef, part: String?): DataContentProvider {
        val source = ref.source ?: return local
        return providers[source]
            ?: throw ContentSourceException(ref.display(), part, "Unknown provider '${source.value}'")
    }
}
