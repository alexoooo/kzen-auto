package tech.kzen.auto.client.objects.document.job.source.catalog

import react.State

external interface CatalogSourceDisplayState: State {
    var catalogState: CatalogStore.State?
    var selection: List<String>
    var symbols: List<String>
    var symbolSearch: String
    var saving: Boolean
    var error: String?
}
