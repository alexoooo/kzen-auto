package tech.kzen.auto.client.objects.document.pipeline.run.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async


class PipelineRunStore(
    private val store: PipelineStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun init() {
        lookupStatus()
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun lookupStatus() {
        val status = ClientContext.restClient.logicStatus()

        store.update { state -> state
            .withRun { it.copy(logicStatus = status) }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun startAndRunAsync() {
        store.update { state -> state
            .withRun { it.copy(
                starting = true,
                runError = null
            ) }
        }

        async {
            delay(1)
            val logicRunId = ClientContext.restClient.logicStart(
                store.mainLocation())

            if (logicRunId == null) {
                store.update { state -> state
                    .withRun { it.copy(
                        starting = false,
                        runError = "Unable to start"
                    ) }
                }
            }
            else {
                delay(10)
                store.update { state -> state
                    .withRun { it.copy(starting = false) }
                }

                delay(10)
                lookupStatus()
            }
        }
    }
}