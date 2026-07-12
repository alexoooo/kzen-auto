@file:Suppress("ConstPropertyName")

package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue


data class StepTrace(
    val state: State,
    val displayValue: ExecutionValue,
    val detail: ExecutionValue,
    val error: String?,

    // Short human-readable diagnostic beside the detail — e.g. which target crop matched and where
    val note: String? = null
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val stateKey = "state"
        private const val displayKey = "display"
        private const val detailKey = "detail"
        private const val errorKey = "error"
        private const val noteKey = "note"


        fun ofExecutionValue(executionValue: ExecutionValue): StepTrace {
            executionValue as MapExecutionValue
            return StepTrace(
                State.valueOf((executionValue.values[stateKey] as TextExecutionValue).value),
                executionValue.values[displayKey]!!,
                executionValue.values[detailKey]!!,
                (executionValue.values[errorKey] as? TextExecutionValue)?.value,
                (executionValue.values[noteKey] as? TextExecutionValue)?.value
            )
        }


        /**
         * [ofExecutionValue] for values that might not be step traces — a run's trace snapshot
         * mixes step traces with other entries (run-root index, worker values), and consumers
         * scanning the whole run (e.g. for traced browser screenshots) need to tell them apart.
         */
        fun ofExecutionValueOrNull(executionValue: ExecutionValue): StepTrace? {
            if (executionValue !is MapExecutionValue) {
                return null
            }

            val stateName = (executionValue.values[stateKey] as? TextExecutionValue)?.value
            val state = State.entries.firstOrNull { it.name == stateName }
                ?: return null

            return StepTrace(
                state,
                executionValue.values[displayKey] ?: return null,
                executionValue.values[detailKey] ?: return null,
                (executionValue.values[errorKey] as? TextExecutionValue)?.value,
                (executionValue.values[noteKey] as? TextExecutionValue)?.value
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    enum class State {
        Idle,
        Active,
        Running,
        Done,

        // Step failed under pause-on-error: rendered as an error and re-run on resume (NOT skipped
        // like Done). Cleared back to Done once a re-run succeeds.
        Error
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(mapOf(
            stateKey to TextExecutionValue(state.name),
            displayKey to displayValue,
            detailKey to detail,
            errorKey to ExecutionValue.of(error),
            noteKey to ExecutionValue.of(note)
        ))
    }
}
