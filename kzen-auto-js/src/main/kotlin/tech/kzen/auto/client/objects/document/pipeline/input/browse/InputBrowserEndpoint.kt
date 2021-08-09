package tech.kzen.auto.client.objects.document.pipeline.input.browse

import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.objects.document.report.listing.InputSelectedInfo
import tech.kzen.auto.common.objects.document.report.spec.input.InputDataSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.store.MirroredGraphError


object InputBrowserEndpoint {

    //-----------------------------------------------------------------------------------------------------------------
    suspend fun selectionDefaultFormats(
        mainLocation: ObjectLocation,
        paths: List<DataLocation>
    ): ClientResult<List<InputDataSpec>> {
        val result = ClientContext.restClient.performDetached(
            mainLocation,
            PipelineConventions.actionParameter to PipelineConventions.actionDefaultFormat,
            *paths.map { PipelineConventions.filesParameter to it.asString() }.toTypedArray())

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as List<Map<String, String>>

                val inputDataSpecs = resultValue.map { InputDataSpec.ofCollection(it) }

                ClientResult.ofSuccess(inputDataSpecs)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }


    suspend fun selectionAddFiles(
        mainLocation: ObjectLocation,
        paths: List<InputDataSpec>
    ): String? {
        val command = InputSpec.addSelectedCommand(mainLocation, paths)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
    }


    suspend fun selectionRemoveFiles(
        mainLocation: ObjectLocation,
        paths: List<InputDataSpec>
    ): String? {
        val command = InputSpec.removeSelectedCommand(mainLocation, paths)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
    }


    suspend fun selectionInfo(
        mainLocation: ObjectLocation
    ): ClientResult<InputSelectedInfo> {
        val result = ClientContext.restClient.performDetached(
            mainLocation,
            ReportConventions.actionParameter to ReportConventions.actionInputSelectionInfo)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as List<Map<String, Any>>

                ClientResult.ofSuccess(
                    InputSelectedInfo.ofCollection(resultValue))
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }
}