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
 * is none to show), an optional validation ERROR (an expression compile failure), and an optional
 * WARNING. Transported over the detached-action wire as an ExecutionValue, keyed by object path in the
 * per-document maps (Script's ScriptValidation, Job's JobValidation).
 *
 * Error vs warning is a hard distinction, not a severity gradient: an error means the step cannot run and the
 * client's Run gate blocks on it; a warning is ADVISORY and never blocks. The context analysis emits only
 * warnings, because a document whose requirement nothing local satisfies may still be perfectly valid when a
 * caller provides it — the editor cannot see the caller, so it says so rather than refusing.
 *
 * [warningMessage] is currently produced only by the Script flavour's context analysis; it simply stays null
 * for Job, which shares this type.
 */
data class StepValidation(
    val typeMetadata: TypeMetadata?,
    val errorMessage: String?,
    val warningMessage: String? = null
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val typeMetadataKey = "type"
        private const val errorMessageKey = "error"
        private const val warningMessageKey = "warning"

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

            // Deliberately NOT the strict lookup the two keys above use: an absent `warning` decodes as null,
            // so a payload written by a peer that predates this field still decodes.
            val warningExecutionValue = executionValue[warningMessageKey]

            val warningMessage =
                if (warningExecutionValue == null || warningExecutionValue == NullExecutionValue) {
                    null
                }
                else {
                    (warningExecutionValue as? TextExecutionValue)
                        ?: throw IllegalArgumentException("'$warningMessageKey' text expected: $executionValue")
                    warningExecutionValue.value
                }

            return StepValidation(typeMetadata, errorMessage, warningMessage)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asExecutionValue(): ExecutionValue {
        val metadataExecutionValue = typeMetadata?.asExecutionValue() ?: NullExecutionValue

        return MapExecutionValue(mapOf(
            typeMetadataKey to metadataExecutionValue,
            errorMessageKey to ExecutionValue.of(errorMessage),
            warningMessageKey to ExecutionValue.of(warningMessage)
        ))
    }
}