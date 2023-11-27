package tech.kzen.auto.common.objects.document.sequence.model

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.model.obj.ObjectPath


data class SequenceValidation(
    val stepValidations: Map<ObjectPath, StepValidation>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofExecutionValue(executionValue: MapExecutionValue): SequenceValidation {
            val stepValidations = executionValue
                .values
                .map {
                    ObjectPath.parse(it.key) to
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