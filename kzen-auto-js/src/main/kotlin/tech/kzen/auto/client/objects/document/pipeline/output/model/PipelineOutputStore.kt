package tech.kzen.auto.client.objects.document.pipeline.output.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputType
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.store.MirroredGraphError


class PipelineOutputStore(
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


    private suspend fun setOutputType(
        outputType: OutputType
    ): String? {
        val command = OutputSpec.changeTypeCommand(
            store.mainLocation(), outputType)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
    }


    fun setOutputTypeAsync(outputType: OutputType) {
        beforeNotationChange()

        async {
            delay(1)
            val notationError = setOutputType(outputType)

            delay(10)
            afterNotationChange(notationError)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun lookupOutput(
//        state: ReportState
    ): ClientResult<OutputInfo> {
//        if (state.columnListing.isNullOrEmpty()) {
//            return ClientResult.ofError("")
//        }

        val result = ClientContext.restClient.performDetached(
            store.mainLocation(),
            PipelineConventions.actionParameter to PipelineConventions.actionOutputInfo)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, Any?>
                val outputInfo = OutputInfo.fromCollection(resultValue)

                ClientResult.ofSuccess(outputInfo)
            }

            is ExecutionFailure -> {
                ClientResult.ofError(result.errorMessage)
            }
        }
    }
}