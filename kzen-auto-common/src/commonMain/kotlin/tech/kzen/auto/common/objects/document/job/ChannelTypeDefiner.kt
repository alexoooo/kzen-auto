package tech.kzen.auto.common.objects.document.job

import tech.kzen.auto.common.objects.document.logic.TypeMetadataDefiner
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames


/**
 * Parses a one-way [tech.kzen.auto.server.objects.job.channel.JobChannel]'s declared element type AND
 * validates its wiring at DEFINITION time (before any run). Bound to the Channel's `elementType` attribute
 * via `by: ChannelTypeDefiner` (see job-jvm.yaml).
 *
 * A Channel carries one declared type — a [TypeMetadata], `Any` when undeclared (an untyped channel). At run
 * time every channel element is a `JobMessage` (the uniform carrier), so the declared type describes the
 * message's PAYLOAD — the strongly typed value a source / Run lane streams — not the physical element. A
 * Worker port referencing the channel may declare its own payload type via the `of:` generic, so the port's
 * metadata type is e.g. `ChannelOutput<String>`. This definer cross-checks them:
 *
 * - **type compatibility** — each producer / consumer port's payload type must match the channel's, with
 *   `Any` on either side acting as a wildcard (an undeclared channel or port is unconstrained — the built-in
 *   Workers all ship untyped, since a flat-consuming stage accepts any payload via the auto-flatten
 *   fallback). A concretely-typed mismatch (e.g. an `Int`-typed 3rd-party producer feeding a `String`
 *   channel) fails here.
 * - **single-reader** — a channel may be drained by at most one consumer port (fan-OUT must be modelled
 *   explicitly, not by implicitly sharing a channel); fan-IN (many producers) is allowed, mirroring
 *   JobChannel's close-on-last-producer.
 *
 * A violation returns [AttributeDefinitionAttempt.failure], naming the offending port; that drops the
 * channel from the graph and surfaces through the same `GraphDefinitionAttempt.failures` -> DefinitionErrors
 * -> StageController path as any other definition error — a pre-run, in-context message rather than a
 * run-time failure. (Element-level safety no longer rests on this check at all: the framework dispatch in
 * [tech.kzen.auto.server.objects.job.worker.TransformWorker] / `SinkWorker` receives the uniform
 * `JobMessage`, failing descriptively on a raw element.) Payload-type FLOW — inferring undeclared types
 * through the graph — is the element-model plan's phase 3.
 *
 * Reads only notation + metadata (DECLARED types), never resolved instances, so two sibling Workers need no
 * mutual definition ordering. Duplex DuplexChannels (request/reply — two element types) are not type-checked
 * in this phase.
 */
@Reflect
class ChannelTypeDefiner: AttributeDefiner {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val channelInputClassName = "tech.kzen.auto.common.paradigm.job.api.ChannelInput"
        private const val channelOutputClassName = "tech.kzen.auto.common.paradigm.job.api.ChannelOutput"
        private const val channelClientClassName = "tech.kzen.auto.common.paradigm.job.api.ChannelClient"
        private const val channelServerClassName = "tech.kzen.auto.common.paradigm.job.api.ChannelServer"

        // A port that DRAINS the channel (the single-reader constraint applies to these); the others FEED it.
        private val consumerPortClassNames = setOf(channelInputClassName, channelServerClassName)
        private val producerPortClassNames = setOf(channelOutputClassName, channelClientClassName)
        private val portClassNames = consumerPortClassNames + producerPortClassNames
    }


    private class PortRef(
        location: ObjectLocation,
        portName: AttributeName,
        val elementType: TypeMetadata?
    ) {
        val label = "${location.objectPath.name.value}.${portName.value}"
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun define(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        partialGraphDefinition: GraphDefinition,
        partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        val graphNotation = graphStructure.graphNotation

        val declaredElementType = when (
            val notation = graphNotation.firstAttribute(objectLocation, attributeName)
        ) {
            is ScalarAttributeNotation -> {
                if (notation.value.isBlank()) {
                    TypeMetadata.any
                }
                else {
                    // Resolve a type-object reference (e.g. `elementType: Int`) to its class; a
                    // dangling ref degrades to Any so a mid-edit notation still starts (cf. readAttributeType).
                    val referenced = graphNotation.coalesce.locateOptional(
                        ObjectReference.parse(notation.value),
                        ObjectReferenceHost.ofLocation(objectLocation))
                    if (referenced == null) {
                        TypeMetadata.any
                    }
                    else {
                        TypeMetadata.of(ClassName(
                            graphNotation.getString(referenced, NotationConventions.classAttributePath)))
                    }
                }
            }

            is MapAttributeNotation -> {
                // Inline TypeMetadata { class, generics, nullable }; an empty map means untyped (Any).
                if (notation.map.isEmpty()) {
                    TypeMetadata.any
                }
                else {
                    TypeMetadataDefiner.parse(notation)
                        ?: return AttributeDefinitionAttempt.failure("Invalid elementType: $notation")
                }
            }

            else ->
                TypeMetadata.any
        }

        val consumers = mutableListOf<PortRef>()
        val producers = mutableListOf<PortRef>()

        for (workerLocation in graphNotation.objectLocations) {
            val objectMetadata = graphStructure.graphMetadata.get(workerLocation)
                ?: continue

            for ((portName, portMetadata) in objectMetadata.attributes.map) {
                val portType = portMetadata.type
                    ?: continue
                val portClassName = portType.className.asString()
                if (portClassName !in portClassNames) {
                    continue
                }

                val portNotation = graphNotation.firstAttribute(workerLocation, portName)
                        as? ScalarAttributeNotation
                    ?: continue
                if (portNotation.value.isBlank()) {
                    continue
                }
                val referenced = graphNotation.coalesce.locateOptional(
                    ObjectReference.parse(portNotation.value),
                    ObjectReferenceHost.ofLocation(workerLocation))
                if (referenced != objectLocation) {
                    continue
                }

                val portRef = PortRef(workerLocation, portName, portType.generics.getOrNull(0))
                if (portClassName in consumerPortClassNames) {
                    consumers.add(portRef)
                }
                else {
                    producers.add(portRef)
                }
            }
        }

        for (port in producers) {
            if (! compatible(port.elementType, declaredElementType)) {
                return mismatch(port, declaredElementType)
            }
        }
        for (port in consumers) {
            if (! compatible(port.elementType, declaredElementType)) {
                return mismatch(port, declaredElementType)
            }
        }

        if (consumers.size > 1) {
            return AttributeDefinitionAttempt.failure(
                "Channel has ${consumers.size} consumers but a channel is single-reader — only one Worker " +
                    "may drain it (${consumers.joinToString { it.label }})")
        }

        return AttributeDefinitionAttempt.success(
            ValueAttributeDefinition(declaredElementType))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun mismatch(port: PortRef, channelElementType: TypeMetadata): AttributeDefinitionAttempt {
        return AttributeDefinitionAttempt.failure(
            "${port.label} is typed ${simple(port.elementType)} but the channel carries " +
                simple(channelElementType))
    }


    private fun compatible(portElementType: TypeMetadata?, channelElementType: TypeMetadata): Boolean {
        if (portElementType == null || isAny(portElementType) || isAny(channelElementType)) {
            return true
        }
        return portElementType == channelElementType
    }


    private fun isAny(typeMetadata: TypeMetadata): Boolean {
        return typeMetadata.className == ClassNames.kotlinAny && typeMetadata.generics.isEmpty()
    }


    private fun simple(typeMetadata: TypeMetadata?): String {
        return typeMetadata?.toSimple() ?: "Any"
    }
}
