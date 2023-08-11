package tech.kzen.auto.common.objects.document.graph

import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
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
        check(attributeName == GraphDocument.edgesAttributeName) {
            "Unexpected attribute name: $attributeName"
        }

        val edgesNotation = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, GraphDocument.edgesAttributeName)
                as? ListAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                        "'Edges' attribute notation not found: $objectLocation - $attributeName")

        val edgeDefinitions = edgesNotation
                .values
                .withIndex()
                .map {
                    EdgeDescriptor.fromNotation(
                            it.index,
                            it.value as MapAttributeNotation
                    )
                }
                .map {
                    ValueAttributeDefinition(it)
                }

        return AttributeDefinitionAttempt.success(
                ListAttributeDefinition(edgeDefinitions))
    }
}