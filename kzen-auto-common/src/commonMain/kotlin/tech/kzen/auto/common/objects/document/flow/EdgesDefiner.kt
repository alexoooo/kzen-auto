package tech.kzen.auto.common.objects.document.flow

import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeDescriptor
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ListAttributeDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class EdgesDefiner: AttributeDefiner {
    override fun define(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        partialGraphDefinition: GraphDefinition,
        partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        check(attributeName == FlowConventions.edgesAttributeName) {
            "Unexpected attribute name: $attributeName"
        }

        val edgesNotation = graphStructure
            .graphNotation
            .firstAttribute(objectLocation, FlowConventions.edgesAttributeName)
            as? ListAttributeNotation
            ?: return AttributeDefinitionAttempt.failure(
                "'Edges' attribute notation not found: $objectLocation - $attributeName")

        val edgeDefinitions = edgesNotation
            .values
            .withIndex()
            .map {
                val edgeDescriptor =
                    try {
                        EdgeDescriptor.fromNotation(
                            it.index,
                            it.value as? MapAttributeNotation
                                ?: throw IllegalArgumentException("Edge must be a map: ${it.value}"))
                    }
                    catch (e: IllegalArgumentException) {
                        // Malformed notation (bad/missing orientation or row/column) fails the
                        // definition with a message instead of crashing it.
                        return AttributeDefinitionAttempt.failure(
                            "Malformed edge ${it.index} in $objectLocation: ${e.message}")
                    }

                ValueAttributeDefinition(edgeDescriptor)
            }

        return AttributeDefinitionAttempt.success(
            ListAttributeDefinition(edgeDefinitions))
    }
}