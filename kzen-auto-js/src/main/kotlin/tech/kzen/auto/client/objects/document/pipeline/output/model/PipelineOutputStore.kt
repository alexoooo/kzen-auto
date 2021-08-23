package tech.kzen.auto.client.objects.document.pipeline.output.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputType
import tech.kzen.lib.common.service.store.MirroredGraphError


class PipelineOutputStore(
    private val store: PipelineStore
) {
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
}