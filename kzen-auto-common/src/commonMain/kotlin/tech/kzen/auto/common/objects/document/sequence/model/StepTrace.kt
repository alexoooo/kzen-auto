@file:Suppress("ConstPropertyName")

package tech.kzen.auto.common.objects.document.sequence.model

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue


data class StepTrace(
    val state: State,
    val displayValue: ExecutionValue,
    val detail: ExecutionValue,
    val error: String?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val stateKey = "state"
        private const val displayKey = "display"
        private const val detailKey = "detail"
        private const val errorKey = "error"


        fun ofExecutionValue(executionValue: ExecutionValue): StepTrace {
            executionValue as MapExecutionValue
            return StepTrace(
                State.valueOf((executionValue.values[stateKey] as TextExecutionValue).value),
                executionValue.values[displayKey]!!,
                executionValue.values[detailKey]!!,
                (executionValue.values[errorKey] as? TextExecutionValue)?.value
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    enum class State {
        Idle,
        Active,
        Running,
        Done
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(mapOf(
            stateKey to TextExecutionValue(state.name),
            displayKey to displayValue,
            detailKey to detail,
            errorKey to ExecutionValue.of(error)
        ))
    }
}
