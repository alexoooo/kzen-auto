package tech.kzen.auto.common.objects.document.graph

import tech.kzen.auto.common.paradigm.dataflow.model.channel.MutableDataflowOutput
import tech.kzen.auto.common.paradigm.dataflow.model.channel.MutableOptionalInput
import tech.kzen.auto.common.paradigm.dataflow.model.channel.MutableRequiredInput
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames


@Suppress("unused")
class DataflowWiring: AttributeDefiner {
    companion object {
        val objectName = ObjectName("DataflowWiring")
        val optionalInputName = ObjectName("OptionalInput")
        val requiredInputName = ObjectName("RequiredInput")


        fun isInput(attributeMetadataMap: MapAttributeNotation): Boolean {
            val isSegment = attributeMetadataMap.values[NotationConventions.isAttributeSegment]
                    as? ScalarAttributeNotation
                    ?: return false

            return isSegment.value == optionalInputName.value ||
                    isSegment.value == requiredInputName.value
        }


        fun findInputs(
                vertexLocation: ObjectLocation,
                graphStructure: GraphStructure
        ): List<AttributeName> {
            val cellMetadata = graphStructure.graphMetadata.objectMetadata[vertexLocation]!!

            return cellMetadata
                    .attributes
                    .values
                    .filter {
                        isInput(it.value.attributeMetadataNotation)
                    }
                    .map {
                        it.key
                    }
        }


        private val optionalOutputClass = ClassName(
                "tech.kzen.auto.common.paradigm.dataflow.api.OptionalOutput")

        private val requiredOutputClass = ClassName(
                "tech.kzen.auto.common.paradigm.dataflow.api.RequiredOutput")

        private val batchOutputClass = ClassName(
                "tech.kzen.auto.common.paradigm.dataflow.api.BatchOutput")

        private val streamOutputClass = ClassName(
                "tech.kzen.auto.common.paradigm.dataflow.api.StreamOutput")


        private val optionalInputClass = ClassName(
                "tech.kzen.auto.common.paradigm.dataflow.api.OptionalInput")

        private val requiredInputClass = ClassName(
                "tech.kzen.auto.common.paradigm.dataflow.api.RequiredInput")
    }


    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val attributeClass: ClassName = graphStructure
                .graphMetadata
                .get(objectLocation)!!
                .attributes
                .values[attributeName]
                ?.type
                ?.className
                ?: ClassNames.kotlinAny

        val value: Any? = when (attributeClass) {
            optionalInputClass ->
                MutableOptionalInput<Any>()

            requiredInputClass ->
                MutableRequiredInput<Any>()


            optionalOutputClass ->
                MutableDataflowOutput<Any>()

            requiredOutputClass ->
                MutableDataflowOutput<Any>()

            batchOutputClass ->
                MutableDataflowOutput<Any>()

            streamOutputClass ->
                MutableDataflowOutput<Any>()


            else ->
                TODO("Unknown: $attributeClass")
        }

        return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(value))
    }
}