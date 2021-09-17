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
import tech.kzen.auto.common.paradigm.common.v1.model.LogicConventions
import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunId
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.store.MirroredGraphError


class PipelineOutputStore(
    private val store: PipelineStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun init() {
        lookupOutputWithFallback()
    }


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
    fun lookupOutputWithFallbackAsync() {
        async {
            lookupOutputWithFallback()
        }
    }


    suspend fun lookupOutputWithFallback() {
        val logicRunInfo = store.state().run.logicStatus?.active

        if (logicRunInfo != null) {
            val runId = logicRunInfo.id
            val executionId = logicRunInfo.frame.executionId
            val onlineResult = outputInfoOnline(runId, executionId)
            val onlineError = onlineResult.errorOrNull()
            if (onlineError == null) {
                store.update { state -> state.withOutput {
                    it.copy(
                        outputInfo = onlineResult.valueOrNull(),
                        outputInfoError = null
                    )
                } }
                return
            }
            else if (! LogicConventions.isMissingError(onlineError, runId, executionId)) {
                store.update { state -> state.withOutput {
                    it.copy(
                        outputInfo = null,
                        outputInfoError = onlineError
                    )
                } }
                return
            }
        }

        if (store.state().output.outputInfo?.status?.isTerminal() == true) {
            return
        }

        val offlineResult = outputInfoOffline()

        store.update { state -> state.withOutput {
            it.copy(
                outputInfo = offlineResult.valueOrNull(),
                outputInfoError = offlineResult.errorOrNull()
            )
        } }

//        if (offlineResult.errorOrNull() == null) {
//            store.run.lookupProgressOffline()
//        }
    }


    private suspend fun outputInfoOnline(
        logicRunId: LogicRunId,
        logicExecutionId: LogicExecutionId
    ): ClientResult<OutputInfo> {
        val result = ClientContext.restClient.performDetached(
            store.mainLocation(),
            PipelineConventions.actionParameter to PipelineConventions.actionOutputInfoOnline,
            LogicConventions.runIdKey to logicRunId.value,
            LogicConventions.executionIdKey to logicExecutionId.value,
        )

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


    private suspend fun outputInfoOffline(): ClientResult<OutputInfo> {
        val result = ClientContext.restClient.performDetached(
            store.mainLocation(),
            PipelineConventions.actionParameter to PipelineConventions.actionOutputInfoOffline)

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


    //-----------------------------------------------------------------------------------------------------------------
    fun resetAsync() {
        if (store.state().isRunningOrLoading()) {
            return
        }

        async {
            delay(1)
            val result = resetRequest()

            delay(10)
            store.update { state -> state
                .withOutput { it.copy(
                    outputInfo = null,
                    outputInfoError = result.errorOrNull()
                ) }
                .withRun { it.copy(
                    progress = null
                ) }
                .withPreviewFiltered { it.copy(
                    tableSummary = null,
                    previewError = null
                ) }
            }
        }
    }


    private suspend fun resetRequest(): ClientResult<Unit> {
        val result = ClientContext.restClient.performDetached(
            store.mainLocation(),
            PipelineConventions.actionParameter to PipelineConventions.actionReset)

        return when (result) {
            is ExecutionSuccess -> {
                ClientResult.emptySuccess
            }

            is ExecutionFailure -> {
                ClientResult.ofError(result.errorMessage)
            }
        }
    }
}