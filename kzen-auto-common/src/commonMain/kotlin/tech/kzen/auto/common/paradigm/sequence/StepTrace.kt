package tech.kzen.auto.common.paradigm.sequence

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.common.model.MapExecutionValue
import tech.kzen.auto.common.paradigm.common.model.TextExecutionValue


data class StepTrace(
    val state: State,
    val displayValue: ExecutionValue,
    val detail: ExecutionValue
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val stateKey = "state"
        private const val displayKey = "display"
        private const val detailKey = "detail"


        fun ofExecutionValue(executionValue: ExecutionValue): StepTrace {
            executionValue as MapExecutionValue
            return StepTrace(
                State.valueOf((executionValue.values[stateKey] as TextExecutionValue).value),
                executionValue.values[displayKey]!!,
                executionValue.values[detailKey]!!
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
            detailKey to detail
        ))
    }
}
