package tech.kzen.auto.common.objects.document.graph

import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.context.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.context.definition.GraphDefinition
import tech.kzen.lib.common.context.definition.ListAttributeDefinition
import tech.kzen.lib.common.context.definition.ValueAttributeDefinition
import tech.kzen.lib.common.context.instance.GraphInstance
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.model.ListAttributeNotation
import tech.kzen.lib.common.structure.notation.model.MapAttributeNotation


@Suppress("unused")
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
                .transitiveAttribute(objectLocation, GraphDocument.edgesAttributeName)
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