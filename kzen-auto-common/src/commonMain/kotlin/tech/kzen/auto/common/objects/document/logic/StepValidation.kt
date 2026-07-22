@file:Suppress("ConstPropertyName")

package tech.kzen.auto.common.objects.document.logic

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


/**
 * The per-object validation unit shared by every Logic flavour's server-side validation pass: the inferred /
 * declared value type (a Script step's expression type; a Job Worker's output payload type — null when there
 * is none to show) plus an optional validation error (an expression compile failure). Transported over the
 * detached-action wire as an ExecutionValue, keyed by object path in the per-document maps
 * (Script's ScriptValidation, Job's JobValidation).
 */
data class StepValidation(
    val typeMetadata: TypeMetadata?,
    val errorMessage: String?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val typeMetadataKey = "type"
        private const val errorMessageKey = "error"

        fun ofMapExecutionValue(executionValue: MapExecutionValue): StepValidation {
            val typeExecutionValue = executionValue[typeMetadataKey]
                ?: throw IllegalArgumentException("'$typeMetadataKey' expected: $executionValue")

            val typeMetadata =
                if (typeExecutionValue == NullExecutionValue) {
                    null
                }
                else {
                    (typeExecutionValue as? MapExecutionValue)
                        ?: throw IllegalArgumentException("'$typeMetadataKey' map expected: $executionValue")

                    TypeMetadata.ofExecutionValue(typeExecutionValue)
                }

            val errorExecutionValue = executionValue[errorMessageKey]
                ?: throw IllegalArgumentException("'$errorMessageKey' expected: $executionValue")

            val errorMessage =
                if (errorExecutionValue == NullExecutionValue) {
                    null
                }
                else {
                    (errorExecutionValue as? TextExecutionValue)
                        ?: throw IllegalArgumentException("'$errorMessageKey' text expected: $executionValue")
                    errorExecutionValue.value
                }

            return StepValidation(typeMetadata, errorMessage)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asExecutionValue(): ExecutionValue {
        val metadataExecutionValue = typeMetadata?.asExecutionValue() ?: NullExecutionValue

        return MapExecutionValue(mapOf(
            typeMetadataKey to metadataExecutionValue,
            errorMessageKey to ExecutionValue.of(errorMessage)
        ))
    }
}