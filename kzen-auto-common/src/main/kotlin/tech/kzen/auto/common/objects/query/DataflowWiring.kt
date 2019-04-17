package tech.kzen.auto.common.objects.query

import tech.kzen.auto.common.paradigm.dataflow.model.mutable.MutableRequiredInput
import tech.kzen.auto.common.paradigm.dataflow.model.mutable.MutableStreamOutput
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.AttributeDefinition
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames


@Suppress("unused")
class DataflowWiring: AttributeDefiner {
    companion object {
        private val streamOutputClass = ClassName(
                "tech.kzen.auto.common.paradigm.dataflow.output.StreamOutput")

        private val requiredInputClass = ClassName(
                "tech.kzen.auto.common.paradigm.dataflow.input.RequiredInput")
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
            streamOutputClass ->
                MutableStreamOutput<Any>()

            requiredInputClass ->
                MutableRequiredInput<Any>()

            else ->
                TODO("Unknown: $attributeClass")
        }

        return ValueAttributeDefinition(value)
    }
}