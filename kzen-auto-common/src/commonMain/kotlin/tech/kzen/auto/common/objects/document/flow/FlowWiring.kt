package tech.kzen.auto.common.objects.document.flow

import tech.kzen.auto.common.paradigm.flow.model.channel.MutableFlowOutput
import tech.kzen.auto.common.paradigm.flow.model.channel.MutableOptionalInput
import tech.kzen.auto.common.paradigm.flow.model.channel.MutableRequiredInput
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames


@Suppress("MemberVisibilityCanBePrivate")
@Reflect
class FlowWiring: AttributeDefiner {
    companion object {
        val objectName = ObjectName("FlowWiring")
        val optionalInputName = ObjectName("OptionalInput")
        val requiredInputName = ObjectName("RequiredInput")


        fun isInput(attributeMetadataMap: MapAttributeNotation): Boolean {
            val isSegment = attributeMetadataMap[NotationConventions.isAttributeSegment]
                as? ScalarAttributeNotation
                ?: return false

            return isSegment.value == optionalInputName.value ||
                isSegment.value == requiredInputName.value
        }


        fun isRequiredInput(attributeMetadataMap: MapAttributeNotation): Boolean {
            val isSegment = attributeMetadataMap[NotationConventions.isAttributeSegment]
                as? ScalarAttributeNotation
                ?: return false

            return isSegment.value == requiredInputName.value
        }


        fun findInputs(
            vertexLocation: ObjectLocation,
            graphStructure: GraphStructure
        ): List<AttributeName> {
            return findInputs(vertexLocation, graphStructure) {
                isInput(it)
            }
        }


        fun findRequiredInputs(
            vertexLocation: ObjectLocation,
            graphStructure: GraphStructure
        ): List<AttributeName> {
            return findInputs(vertexLocation, graphStructure) {
                isRequiredInput(it)
            }
        }


        private fun findInputs(
            vertexLocation: ObjectLocation,
            graphStructure: GraphStructure,
            predicate: (MapAttributeNotation) -> Boolean
        ): List<AttributeName> {
            val cellMetadata = graphStructure.graphMetadata.objectMetadata[vertexLocation]!!

            return cellMetadata
                .attributes
                .map
                .filter {
                    predicate(it.value.attributeMetadataNotation)
                }
                .map {
                    it.key
                }
        }


        private val optionalOutputClass = ClassName(
                "tech.kzen.auto.common.paradigm.flow.api.OptionalOutput")

        private val requiredOutputClass = ClassName(
                "tech.kzen.auto.common.paradigm.flow.api.RequiredOutput")

        private val batchOutputClass = ClassName(
                "tech.kzen.auto.common.paradigm.flow.api.BatchOutput")

        private val streamOutputClass = ClassName(
                "tech.kzen.auto.common.paradigm.flow.api.StreamOutput")


        private val optionalInputClass = ClassName(
                "tech.kzen.auto.common.paradigm.flow.api.OptionalInput")

        private val requiredInputClass = ClassName(
                "tech.kzen.auto.common.paradigm.flow.api.RequiredInput")
    }


    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
//        @Suppress("MoveVariableDeclarationIntoWhen")
        val attributeClass: ClassName = graphStructure
                .graphMetadata
                .get(objectLocation)!!
                .attributes[attributeName]
                ?.type
                ?.className
                ?: ClassNames.kotlinAny

        val value: Any? = when (attributeClass) {
            optionalInputClass ->
                MutableOptionalInput<Any>()

            requiredInputClass ->
                MutableRequiredInput<Any>()


            optionalOutputClass ->
                MutableFlowOutput<Any>()

            requiredOutputClass ->
                MutableFlowOutput<Any>()

            batchOutputClass ->
                MutableFlowOutput<Any>()

            streamOutputClass ->
                MutableFlowOutput<Any>()


            else ->
                return AttributeDefinitionAttempt.failure(
                    "Unknown flow channel type '$attributeClass': $objectLocation - $attributeName")
        }

        return AttributeDefinitionAttempt.success(
            ValueAttributeDefinition(value))
    }
}