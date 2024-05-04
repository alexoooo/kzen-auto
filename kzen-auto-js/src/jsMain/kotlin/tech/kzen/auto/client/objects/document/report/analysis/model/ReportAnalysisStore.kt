package tech.kzen.auto.client.objects.document.report.analysis.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.report.model.ReportStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisType
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueType
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.service.store.MirroredGraphError


class ReportAnalysisStore(
    private val store: ReportStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun mainLocation(): ObjectLocation {
        return store.mainLocation()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun beforeNotationChange() {
        store.update { state -> state
            .withNotationError(null)
        }
    }


    private fun afterNotationChange(error: String?) {
        store.update { state -> state
            .withNotationError(error)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun clearPivotRowsAsync() {
        val command = PivotSpec.clearRowCommand(
            store.mainLocation())

        applyCommandAsync(command, true)
    }


    fun addPivotRowAsync(headerLabel: HeaderLabel) {
        val command = PivotSpec.addRowCommand(
            store.mainLocation(), headerLabel)

        applyCommandAsync(command, true)
    }


    fun removePivotRowAsync(headerLabel: HeaderLabel) {
        val command = PivotSpec.removeRowCommand(
            store.mainLocation(), headerLabel)

        applyCommandAsync(command, true)
    }


    fun addValueType(headerLabel: HeaderLabel, valueType: PivotValueType) {
        val command = PivotSpec.addValueTypeCommand(
            store.mainLocation(), headerLabel, valueType)

        applyCommandAsync(command, true)
    }


    fun removeValueType(columnName: HeaderLabel, valueType: PivotValueType) {
        val command = PivotSpec.removeValueTypeCommand(
            store.mainLocation(), columnName, valueType)

        applyCommandAsync(command, true)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun addValue(headerLabel: HeaderLabel) {
        val command = PivotSpec.addValueCommand(
            store.mainLocation(), headerLabel)

        applyCommand(command, true)
    }


    fun removeValueAsync(headerLabel: HeaderLabel) {
        val command = PivotSpec.removeValueCommand(
            store.mainLocation(), headerLabel)

        applyCommandAsync(command, true)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun setAnalysisTypeAsync(analysisType: AnalysisType) {
        val command = AnalysisSpec.changeTypeCommand(
            store.mainLocation(), analysisType)

        applyCommandAsync(command, true)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun applyCommandAsync(
        command: NotationCommand,
        refreshOutput: Boolean
    ) {
        async {
            applyCommand(command, refreshOutput)
        }
    }


    private suspend fun applyCommand(
        command: NotationCommand,
        refreshOutput: Boolean = false
    ) {
        delay(1)
        beforeNotationChange()

        val result = ClientContext.mirroredGraphStore.apply(command)

        val notationError = (result as? MirroredGraphError)?.error?.message

        delay(10)
        afterNotationChange(notationError)

        if (notationError == null && refreshOutput) {
            store.output.lookupOutputWithFallback()
        }
    }
}