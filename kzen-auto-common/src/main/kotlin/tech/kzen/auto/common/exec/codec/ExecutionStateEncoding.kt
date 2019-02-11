package tech.kzen.auto.common.exec.codec

import tech.kzen.auto.common.exec.ExecutionState
import tech.kzen.auto.common.objects.service.ActionManager
import tech.kzen.lib.common.api.model.ObjectLocation


data class ExecutionStateEncoding(
        val running: Boolean,
        val previous: ExecutionResultEncoding?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun encode(
                executionState: ExecutionState,
                objectLocation: ObjectLocation,
                actionManager: ActionManager
        ): ExecutionStateEncoding {
            return ExecutionStateEncoding(
                    executionState.running,
                    executionState.previous?.let {
                        ExecutionResultEncoding.encode(it.result, objectLocation, actionManager)
                    }
            )
        }


        fun toCollection(result: ExecutionStateEncoding): Map<String, Any?> {
            return mapOf(
                    "running" to result.running,
                    "previous" to result.previous?.let{
                        ExecutionResultEncoding.toCollection(it)
                    }
            )
        }


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any?>): ExecutionStateEncoding {
            return ExecutionStateEncoding(
                    collection["running"] as Boolean,
                    collection["previous"]?.let {
                        ExecutionResultEncoding.fromCollection(it as Map<String, String?>)
                    }
            )
        }
    }


    fun decode(): ExecutionState {
        return ExecutionState(
                running,
                previous?.let {
                    ExecutionState.Outcome(
                            it.decode(),
                            it.digest()
                    )
                }
        )
    }
}