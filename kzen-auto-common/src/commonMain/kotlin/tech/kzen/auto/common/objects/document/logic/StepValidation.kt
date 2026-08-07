@file:Suppress("ConstPropertyName")

package tech.kzen.auto.common.objects.document.logic

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
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
 * client's Run gate blocks on it; a warning is ADVISORY and never blocks. The context analysis emits both — an
 * unsatisfiable requirement is an error, while a dangling reference or an unbackable export is a warning — and
 * a step can carry one of each, an expression compile failure joined with a context error.
 *
 * [warningMessage] is produced only by the Script flavour's context analysis; it simply stays null for Job,
 * which shares this type.
 *
 * [errorOffset] locates [errorMessage] within the object's own expression text — a character offset the
 * editor marks — and is absent whenever the error has no position there. When several findings are joined
 * into one [errorMessage] the compile error is joined FIRST, so the offset describes the leading part; the
 * rest of a joined message has no position by nature.
 */
data class StepValidation(
    val typeMetadata: TypeMetadata?,
    val errorMessage: String?,
    val warningMessage: String? = null,
    val errorOffset: Int? = null
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val typeMetadataKey = "type"
        private const val errorMessageKey = "error"
        private const val warningMessageKey = "warning"
        private const val errorOffsetKey = "errorOffset"

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

            // Lenient for the same reason as `warning` above.
            val errorOffsetExecutionValue = executionValue[errorOffsetKey]

            val errorOffset =
                if (errorOffsetExecutionValue == null || errorOffsetExecutionValue == NullExecutionValue) {
                    null
                }
                else {
                    (errorOffsetExecutionValue as? NumberExecutionValue)
                        ?: throw IllegalArgumentException("'$errorOffsetKey' number expected: $executionValue")
                    errorOffsetExecutionValue.value.toInt()
                }

            return StepValidation(typeMetadata, errorMessage, warningMessage, errorOffset)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asExecutionValue(): ExecutionValue {
        val metadataExecutionValue = typeMetadata?.asExecutionValue() ?: NullExecutionValue

        return MapExecutionValue(mapOf(
            typeMetadataKey to metadataExecutionValue,
            errorMessageKey to ExecutionValue.of(errorMessage),
            warningMessageKey to ExecutionValue.of(warningMessage),
            errorOffsetKey to ExecutionValue.of(errorOffset)
        ))
    }
}