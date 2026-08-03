package tech.kzen.auto.common.objects.document.script

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.exec.engine.disposal.SettleDisposalPolicy
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect


// Coerces an anonymous-disposal notation scalar into a SettleDisposalPolicy at definition time (once), so
// the registering step's constructor receives the enum directly and an invalid value fails definition rather
// than execution. Bound via the SettleDisposalPolicy type's meta.ref (mirrors ResourceClosePolicyDefiner).
//
// The wire spellings live here rather than on the enum, which — unlike ResourceClosePolicy — carries no
// notation vocabulary of its own: it is a pure engine type, so its notation surface belongs to the layer
// that introduces it. Written out rather than derived from the constant names, because this is a format
// users type into notation and it must not shift when a constant is renamed.
@Reflect
class SettleDisposalPolicyDefiner: AttributeDefiner {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val byKey = mapOf(
            "auto" to SettleDisposalPolicy.Auto,
            "keepOnFailure" to SettleDisposalPolicy.KeepOnFailure)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun define(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        partialGraphDefinition: GraphDefinition,
        partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        val notation = graphStructure
            .graphNotation
            .firstAttribute(objectLocation, attributeName) as? ScalarAttributeNotation
            ?: return AttributeDefinitionAttempt.failure(
                "'$attributeName' must be a scalar: $objectLocation")

        val policy = byKey[notation.value]
            ?: return AttributeDefinitionAttempt.failure(
                "Unknown '$attributeName' value '${notation.value}', " +
                        "expected one of: ${byKey.keys.joinToString(", ")}")

        return AttributeDefinitionAttempt.success(
            ValueAttributeDefinition(policy))
    }
}
