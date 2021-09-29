package tech.kzen.auto.client.objects.document.pipeline.analysis.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisType
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueType
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.service.store.MirroredGraphError


class PipelineAnalysisStore(
    private val store: PipelineStore
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


    fun addPivotRowAsync(columnName: String) {
        val command = PivotSpec.addRowCommand(
            store.mainLocation(), columnName)

        applyCommandAsync(command, true)
    }


    fun removePivotRowAsync(columnName: String) {
        val command = PivotSpec.removeRowCommand(
            store.mainLocation(), columnName)

        applyCommandAsync(command, true)
    }


    fun addValueType(columnName: String, valueType: PivotValueType) {
        val command = PivotSpec.addValueTypeCommand(
            store.mainLocation(), columnName, valueType)

        applyCommandAsync(command, true)
    }


    fun removeValueType(columnName: String, valueType: PivotValueType) {
        val command = PivotSpec.removeValueTypeCommand(
            store.mainLocation(), columnName, valueType)

        applyCommandAsync(command, true)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun addValue(columnName: String) {
        val command = PivotSpec.addValueCommand(
            store.mainLocation(), columnName)

        applyCommand(command, true)
    }


    fun removeValueAsync(columnName: String) {
        val command = PivotSpec.removeValueCommand(
            store.mainLocation(), columnName)

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

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        val notationError = (result as? MirroredGraphError)?.error?.message

        delay(10)
        afterNotationChange(notationError)

        if (notationError == null && refreshOutput) {
            store.output.lookupOutputWithFallback()
        }
    }
}