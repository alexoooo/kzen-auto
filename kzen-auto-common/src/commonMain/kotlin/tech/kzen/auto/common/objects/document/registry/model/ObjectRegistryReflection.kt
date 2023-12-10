package tech.kzen.auto.common.objects.document.registry.model

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue


data class ObjectRegistryReflection(
    val source: String?,
    val error: String?
) {
    companion object {
        private const val sourceKey = "source"
        private const val errorKey = "error"

        fun ofExecutionValue(executionValue: MapExecutionValue): ObjectRegistryReflection {
            return ObjectRegistryReflection(
                (executionValue[sourceKey] as? TextExecutionValue)?.value,
                (executionValue[errorKey] as? TextExecutionValue)?.value
            )
        }
    }


    fun asExecutionValue(): MapExecutionValue {
        return MapExecutionValue(mapOf(
            sourceKey to ExecutionValue.of(source),
            errorKey to ExecutionValue.of(error)
        ))
    }
}