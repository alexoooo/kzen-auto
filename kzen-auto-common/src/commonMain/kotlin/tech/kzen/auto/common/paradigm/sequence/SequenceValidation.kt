package tech.kzen.auto.common.paradigm.sequence

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation


data class SequenceValidation(
    val stepValidations: Map<ObjectLocation, StepValidation>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofExecutionValue(executionValue: MapExecutionValue): SequenceValidation {
            val stepValidations = executionValue
                .values
                .map {
                    ObjectLocation.parse(it.key) to
                            StepValidation.ofMapExecutionValue(it.value as MapExecutionValue)
                }
                .toMap()

            return SequenceValidation(stepValidations)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(
            stepValidations.map {
                it.key.asString() to it.value.asExecutionValue()
            }.toMap())
    }
}