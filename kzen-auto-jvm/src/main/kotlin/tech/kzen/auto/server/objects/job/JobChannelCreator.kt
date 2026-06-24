package tech.kzen.auto.server.objects.job

import tech.kzen.auto.server.objects.job.channel.DuplexJobChannel
import tech.kzen.auto.server.objects.job.channel.JobChannel
import tech.kzen.lib.common.api.AttributeCreator
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.platform.ClassName


/**
 * Wires a Worker's channel-endpoint constructor parameter to the single shared channel instance it
 * references. A Worker declares zero or more arbitrarily-named channel attributes, each carrying a scalar
 * reference to a named Channel object (e.g. `out: rawRecords`, `serve: liveQuery`). The default
 * [tech.kzen.lib.common.objects.base.StructuralAttributeDefiner] already turns that scalar into a strong
 * reference — which both makes the Channel construct before the Worker and lets this creator read which
 * Channel is meant — so the only custom step is *creation*: resolving the shared channel and handing back
 * the right endpoint view.
 *
 * Dispatch is on the attribute's declared TYPE, never its name:
 * - over a one-way [JobChannel]: a [tech.kzen.auto.common.paradigm.job.api.ChannelInput]-typed attribute
 *   receives `channel.input` (the single fan-in/fan-out consuming view); a
 *   [tech.kzen.auto.common.paradigm.job.api.ChannelOutput]-typed attribute receives a fresh
 *   `channel.newProducer()` (so close-on-last-producer tracks each output endpoint).
 * - over a duplex [DuplexJobChannel]: a [tech.kzen.auto.common.paradigm.job.api.ChannelClient]-typed
 *   attribute receives a fresh `channel.newClient()` (so close-on-last-client tracks each client endpoint);
 *   a [tech.kzen.auto.common.paradigm.job.api.ChannelServer]-typed attribute receives the single
 *   `channel.server` serving ("actor") view.
 *
 * Selected per attribute via the `creator: JobChannelCreator` metadata key (see job-jvm.yaml). Because
 * resolving an instance is only possible once the graph is being created — not during definition, where no
 * instances exist yet — this is an [AttributeCreator] rather than an
 * [tech.kzen.lib.common.api.AttributeDefiner] like Flow's `FlowWiring` (whose per-vertex channels are fresh
 * holders connected later by the edge matrix, not shared objects resolved by reference).
 */
@Reflect
object JobChannelCreator: AttributeCreator {
    //-----------------------------------------------------------------------------------------------------------------
    private val channelInputClassName = ClassName(
        "tech.kzen.auto.common.paradigm.job.api.ChannelInput")

    private val channelOutputClassName = ClassName(
        "tech.kzen.auto.common.paradigm.job.api.ChannelOutput")

    private val channelClientClassName = ClassName(
        "tech.kzen.auto.common.paradigm.job.api.ChannelClient")

    private val channelServerClassName = ClassName(
        "tech.kzen.auto.common.paradigm.job.api.ChannelServer")


    //-----------------------------------------------------------------------------------------------------------------
    override fun create(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        objectDefinition: ObjectDefinition,
        partialGraphInstance: GraphInstance,
        environment: GraphEnvironment
    ): Any? {
        val attributeClassName = graphStructure
            .graphMetadata
            .get(objectLocation)
            ?.attributes
            ?.get(attributeName)
            ?.type
            ?.className
            ?: throw IllegalArgumentException(
                "Channel attribute type missing: $objectLocation - $attributeName")

        val attributeDefinition = objectDefinition.attributeDefinitions.map[attributeName]
            as? ReferenceAttributeDefinition
            ?: throw IllegalArgumentException(
                "Channel attribute must reference a Channel object: $objectLocation - $attributeName")

        val channelReference = attributeDefinition.objectReference
            ?: throw IllegalArgumentException(
                "Channel reference is empty: $objectLocation - $attributeName")

        val channelLocation = partialGraphInstance.objectInstances.locate(
            channelReference, ObjectReferenceHost.ofLocation(objectLocation))

        val channelInstance = partialGraphInstance[channelLocation]?.reference
            ?: throw IllegalArgumentException(
                "Referenced Channel not found: $objectLocation - $attributeName - $channelLocation")

        return when (attributeClassName) {
            channelInputClassName ->
                oneWay(channelInstance, objectLocation, attributeName, channelLocation).input

            channelOutputClassName ->
                oneWay(channelInstance, objectLocation, attributeName, channelLocation).newProducer()

            channelClientClassName ->
                duplex(channelInstance, objectLocation, attributeName, channelLocation).newClient()

            channelServerClassName ->
                duplex(channelInstance, objectLocation, attributeName, channelLocation).server

            else ->
                throw IllegalArgumentException(
                    "Channel attribute must be ChannelInput / ChannelOutput / ChannelClient / ChannelServer: " +
                        "$objectLocation - $attributeName - $attributeClassName")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun oneWay(
        channelInstance: Any?,
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        channelLocation: ObjectLocation
    ): JobChannel =
        channelInstance as? JobChannel
            ?: throw IllegalArgumentException(
                "ChannelInput / ChannelOutput must reference a one-way Channel: " +
                    "$objectLocation - $attributeName - $channelLocation")


    private fun duplex(
        channelInstance: Any?,
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        channelLocation: ObjectLocation
    ): DuplexJobChannel =
        channelInstance as? DuplexJobChannel
            ?: throw IllegalArgumentException(
                "ChannelClient / ChannelServer must reference a DuplexChannel: " +
                    "$objectLocation - $attributeName - $channelLocation")
}
