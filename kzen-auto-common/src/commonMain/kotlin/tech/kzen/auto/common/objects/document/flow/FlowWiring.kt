package tech.kzen.auto.common.objects.document.flow

import tech.kzen.auto.common.paradigm.flow.model.channel.FlowOutputKind
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
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames


/**
 * Mints the live channel object behind a flow vertex's input/output attribute, chosen from the channel marker
 * the attribute's metadata declares. Structural queries about which attributes ARE channels belong to the
 * paradigm rather than here — see `FlowStructureConventions`.
 */
@Reflect
class FlowWiring: AttributeDefiner {
    companion object {
        // KEEP IN SYNC with the matching `class:` lines in notation/auto-common/common-flow.yaml. These are
        // compared as strings against the channel marker's declared type, so the pair only has to AGREE — which
        // is why both sides silently carried the pre-`input`/`output`-subpackage names for as long as they did.
        private val optionalOutputClass = ClassName(
                "tech.kzen.auto.common.paradigm.flow.api.output.OptionalOutput")

        private val requiredOutputClass = ClassName(
                "tech.kzen.auto.common.paradigm.flow.api.output.RequiredOutput")

        private val batchOutputClass = ClassName(
                "tech.kzen.auto.common.paradigm.flow.api.output.BatchOutput")

        private val streamOutputClass = ClassName(
                "tech.kzen.auto.common.paradigm.flow.api.output.StreamOutput")


        private val optionalInputClass = ClassName(
                "tech.kzen.auto.common.paradigm.flow.api.input.OptionalInput")

        private val requiredInputClass = ClassName(
                "tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput")
    }


    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        val attributeClass: ClassName = graphStructure
                .graphMetadata
                .get(objectLocation)!!
                .attributes[attributeName]
                ?.type
                ?.className
                ?: ClassNames.kotlinAny

        val channelLabel = "${attributeName.value} of $objectLocation"

        val value: Any? = when (attributeClass) {
            optionalInputClass ->
                MutableOptionalInput<Any>()

            requiredInputClass ->
                MutableRequiredInput<Any>()


            optionalOutputClass ->
                MutableFlowOutput<Any>(FlowOutputKind.Optional, channelLabel)

            requiredOutputClass ->
                MutableFlowOutput<Any>(FlowOutputKind.Required, channelLabel)

            batchOutputClass ->
                MutableFlowOutput<Any>(FlowOutputKind.Batch, channelLabel)

            streamOutputClass ->
                MutableFlowOutput<Any>(FlowOutputKind.Stream, channelLabel)


            else ->
                return AttributeDefinitionAttempt.failure(
                    "Unknown flow channel type '$attributeClass': $objectLocation - $attributeName")
        }

        return AttributeDefinitionAttempt.success(
            ValueAttributeDefinition(value))
    }
}