package tech.kzen.auto.common.objects.document.target

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.*
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * Defines the `target:` notation map — `{type, value?, policy?, index?}` — against the
 * registered target types: the `type:` key resolves to the `is: TargetSpecType` notation object
 * with the matching `typeName:`, whose declared `valueKind:` (`none | text | reference`) drives
 * the value definition. Purely notation-driven, so adding a target type requires no edit here
 * (a definer cannot take autowired instances — it is instantiated mid-definition, before the
 * handler objects exist; the code-bearing create side is [TargetSpecCreator]).
 */
@Reflect
class TargetSpecDefiner: AttributeDefiner {
    companion object {
        val targetAttributeName = AttributeName("target")

        const val typeKey = "type"
        val typeSegment = AttributeSegment.ofKey(typeKey)

        const val valueKey = "value"
        val valueSegment = AttributeSegment.ofKey(valueKey)

        // The registration marker archetype (common-action.yaml) and its per-type attributes
        private val targetSpecTypeArchetype = ObjectReference.parse("TargetSpecType")
        private val typeNameAttributeName = AttributeName("typeName")
        private val valueKindAttributeName = AttributeName("valueKind")

        const val valueKindNone = "none"
        const val valueKindText = "text"
        const val valueKindReference = "reference"
    }


    override fun define(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        partialGraphDefinition: GraphDefinition,
        partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        check(attributeName == targetAttributeName) {
            "Unexpected attribute name: $attributeName"
        }

        val targetNotation = graphStructure
            .graphNotation
            .firstAttribute(objectLocation, targetAttributeName)
            as? MapAttributeNotation
            ?: return AttributeDefinitionAttempt.failure(
                "'Target' attribute notation not found: $objectLocation - $attributeName")

        val typeName = targetNotation[typeKey]?.asString()
            ?: return AttributeDefinitionAttempt.failure(
                "Target 'type' not found: $objectLocation")

        val typeLocation = findTypeObject(typeName, graphStructure, objectLocation)
            ?: return AttributeDefinitionAttempt.failure(
                "Unknown target 'type': $typeName - $objectLocation")

        val policyName = targetNotation.get(TargetMatchPolicy.policyKey)?.asString()
        val policyIndex = targetNotation.get(TargetMatchPolicy.indexKey)?.asString()?.toIntOrNull()
        TargetMatchPolicy.parse(policyName, policyIndex)
            ?: return AttributeDefinitionAttempt.failure(
                "Unknown target 'policy': $policyName - $objectLocation")

        val valueKind = graphStructure
            .graphNotation
            .firstAttribute(typeLocation, valueKindAttributeName)
            .asString()

        val valueDefinition: AttributeDefinition?
        when (valueKind) {
            valueKindNone ->
                valueDefinition = null

            valueKindText -> {
                val value = targetNotation.get(valueKey)?.asString()
                    ?: return AttributeDefinitionAttempt.failure(
                        "Target $typeName 'value' not found: $objectLocation")

                valueDefinition = ValueAttributeDefinition(value)
            }

            valueKindReference -> {
                val value = targetNotation.get(valueKey)?.asString()
                    ?: return AttributeDefinitionAttempt.failure(
                        "Target $typeName 'value' not found: $objectLocation")

                valueDefinition = ReferenceAttributeDefinition(
                    ObjectReference.parse(value),
                    weak = false,
                    nullable = false)
            }

            else ->
                return AttributeDefinitionAttempt.failure(
                    "Target type 'valueKind' invalid: $valueKind - $typeLocation")
        }

        val definitionMap = mutableMapOf<String, AttributeDefinition>()
        definitionMap[typeKey] = ValueAttributeDefinition(typeName)

        if (valueDefinition != null) {
            definitionMap[valueKey] = valueDefinition
        }

        if (policyName != null) {
            definitionMap[TargetMatchPolicy.policyKey] = ValueAttributeDefinition(policyName)
            if (policyIndex != null) {
                definitionMap[TargetMatchPolicy.indexKey] = ValueAttributeDefinition(policyIndex)
            }
        }

        return AttributeDefinitionAttempt.success(
                MapAttributeDefinition(definitionMap))
    }


    /** The `is: TargetSpecType` notation object whose `typeName:` matches (the same direct-`is`
     *  matching the Autowired definer uses, so registration and dispatch agree). */
    private fun findTypeObject(
        typeName: String,
        graphStructure: GraphStructure,
        objectLocation: ObjectLocation
    ): ObjectLocation? {
        val objectReferenceHost = ObjectReferenceHost.ofLocation(objectLocation)

        val archetypeLocation = graphStructure.graphNotation.coalesce.locateOptional(
            targetSpecTypeArchetype, objectReferenceHost)
            ?: return null

        for ((location, notation) in graphStructure.graphNotation.coalesce.map) {
            // NB: 'is' may be a single reference or, under multiple inheritance, a list of them
            val isReferences = when (val isAttribute = notation.attributes.map[NotationConventions.isAttributeName]) {
                is ScalarAttributeNotation -> listOf(isAttribute.value)
                is ListAttributeNotation -> isAttribute.values.mapNotNull { it.asString() }
                else -> continue
            }

            val matchesArchetype = isReferences.any { isReference ->
                graphStructure.graphNotation.coalesce.locateOptional(
                    ObjectReference.parse(isReference), objectReferenceHost
                ) == archetypeLocation
            }
            if (!matchesArchetype) {
                continue
            }

            val registeredTypeName = graphStructure
                .graphNotation
                .firstAttribute(location, typeNameAttributeName)
                .asString()

            if (registeredTypeName == typeName) {
                return location
            }
        }

        return null
    }
}
