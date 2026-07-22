package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.auto.common.objects.document.logic.StepValidation
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.model.obj.ObjectPath


data class ScriptValidation(
    val stepValidations: Map<ObjectPath, StepValidation>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofExecutionValue(executionValue: MapExecutionValue): ScriptValidation {
            val stepValidations = executionValue
                .values
                .map {
                    ObjectPath.parse(it.key) to
                            StepValidation.ofMapExecutionValue(it.value as MapExecutionValue)
                }
                .toMap()

            return ScriptValidation(stepValidations)
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