package tech.kzen.auto.client.objects.document.report.filter.model

import tech.kzen.auto.client.objects.document.report.model.ReportStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.spec.filter.ColumnFilterType
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.service.store.MirroredGraphError


class ReportFilterStore(
    private val store: ReportStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun mainLocation(): ObjectLocation {
        return store.mainLocation()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun beforeRequest() {
        store.update { state ->
            state.withFilter { it.copy(
                filterError = null,
                filterLoading = true
            ) }
        }
    }


    private fun afterRequest(error: String?) {
        store.update { state ->
            state.withFilter { it.copy(
                filterError = error,
                filterLoading = false
            ) }
        }
    }


    private suspend fun refreshOutputAndPreviewIfRequired() {
        store.output.lookupOutputOffline()

        val hasOutput = (store.state().output.outputInfo?.status ?: OutputStatus.Missing) != OutputStatus.Missing
        if (hasOutput) {
            store.previewFiltered.lookupSummaryWithFallback()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun addFilterAsync(
        columnName: String
    ) {
        beforeRequest()
        async {
            val error = addColumn(columnName)
            afterRequest(error)
        }
    }


    private suspend fun addColumn(
        columnName: String
    ): String? {
        return editNotation(
            FilterSpec.addCommand(
                store.mainLocation(), columnName))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun removeFilterAsync(
        columnName: HeaderLabel
    ) {
        beforeRequest()
        async {
            val isEmpty = store.state().filterSpec().columns[columnName]?.isEmpty() ?: true

            val error = removeColumn(columnName)
            afterRequest(error)

            if (! isEmpty) {
                refreshOutputAndPreviewIfRequired()
            }
        }
    }


    private suspend fun removeColumn(
        columnName: HeaderLabel
    ): String? {
        return editNotation(
            FilterSpec.removeCommand(
                store.mainLocation(), columnName))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun changeFilterTypeAsync(
        columnName: HeaderLabel, type: ColumnFilterType
    ) {
        beforeRequest()
        async {
            val isEmpty = store.state().filterSpec().columns[columnName]?.isEmpty() ?: true

            val error = updateType(columnName, type)
            afterRequest(error)

            if (! isEmpty) {
                refreshOutputAndPreviewIfRequired()
            }
        }
    }


    private suspend fun updateType(
        columnName: HeaderLabel, type: ColumnFilterType
    ): String? {
        return editNotation(
            FilterSpec.updateTypeCommand(
                store.mainLocation(), columnName, type))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun addFilterValueAsync(
        columnName: HeaderLabel, filterValue: String
    ) {
        beforeRequest()
        async {
            val error = addValue(columnName, filterValue)
            afterRequest(error)
            refreshOutputAndPreviewIfRequired()
        }
    }


    fun removeFilterValueAsync(
        columnName: HeaderLabel, filterValue: String
    ) {
        beforeRequest()
        async {
            val error = removeValue(columnName, filterValue)
            afterRequest(error)
            refreshOutputAndPreviewIfRequired()
        }
    }


    private suspend fun addValue(
        columnName: HeaderLabel, filterValue: String
    ): String? {
        return editNotation(
            FilterSpec.addValueCommand(
                store.mainLocation(), columnName, filterValue))
    }


    private suspend fun removeValue(
        columnName: HeaderLabel, filterValue: String
    ): String? {
        return editNotation(
            FilterSpec.removeValueCommand(
                store.mainLocation(), columnName, filterValue))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun changedByEdit() {
        async {
            refreshOutputAndPreviewIfRequired()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun editNotation(
        command: NotationCommand
    ): String? {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)
            ?.error
            ?.let { it.message ?: "$result" }
    }
}