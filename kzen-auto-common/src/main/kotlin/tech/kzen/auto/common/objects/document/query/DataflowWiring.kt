package tech.kzen.auto.common.objects.document.query

import tech.kzen.auto.common.paradigm.dataflow.model.chanel.MutableDataflowOutput
import tech.kzen.auto.common.paradigm.dataflow.model.chanel.MutableRequiredInput
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.context.definition.AttributeDefinition
import tech.kzen.lib.common.context.definition.GraphDefinition
import tech.kzen.lib.common.context.definition.ValueAttributeDefinition
import tech.kzen.lib.common.context.instance.GraphInstance
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames


@Suppress("unused")
class DataflowWiring: AttributeDefiner {
    companion object {
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
    ): AttributeDefinition {
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
                MutableRequiredInput<Any>()

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

        return ValueAttributeDefinition(value)
    }
}