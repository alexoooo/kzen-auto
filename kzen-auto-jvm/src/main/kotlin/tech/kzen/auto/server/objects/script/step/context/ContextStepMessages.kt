package tech.kzen.auto.server.objects.script.step.context

import tech.kzen.auto.common.objects.document.logic.context.LogicContextConventions
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation


/**
 * The validation error a generic Context step reports when its declaration yields no descriptor to type
 * against. [LogicContextConventions.stepBinds] / [LogicContextConventions.stepUses] come back empty in two
 * different situations — nothing declared, and a declared name that resolves to no Context — and only the
 * raw reference strings tell them apart. Worth telling apart because the fixes differ: pick a Context versus
 * repair a name.
 */
object ContextStepMessages {
    fun unresolvedDeclaration(
        graphNotation: GraphNotation,
        stepLocation: ObjectLocation,
        attributePath: AttributePath,
        absent: String
    ): String {
        val references = LogicContextConventions.stepContextReferences(
            graphNotation, stepLocation, attributePath)

        return when {
            references.isEmpty() -> absent
            else -> "Not a context: ${references.joinToString()}"
        }
    }
}
