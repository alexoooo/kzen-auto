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
 * time every channel element is a `DataValue` (the uniform carrier), so the declared type describes the
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
 * `DataValue`, failing descriptively on a raw element.) Payload-type FLOW — inferring undeclared types
 * through the graph — is the separate static walk (the server-side JobValidator: inferred types thread into
 * each Worker's expression receiver and display on its card); this definer stays the DECLARED-type check.
 *
 * Reads only notation + metadata (DECLARED types), never resolved instances, so two sibling Workers need no
 * mutual definition ordering. Duplex DuplexChannels (request/reply — two element types) are not type-checked
 * in this phase.
 */
@Reflect
class ChannelTypeDefiner: AttributeDefiner {
    //-----------------------------------------------------------------------------------------------------------------
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

        // A Job's Workers and its Channels are one document — synthesis mints channels into the Job's own
        // notation and a port names a sibling — so the ports that can reach this channel are its document's,
        // and scanning the whole project would re-walk every unrelated document per channel defined.
        val hostDocument = objectLocation.documentPath
        val hostObjectPaths = graphNotation.documents[hostDocument]
            ?.objects
            ?.notations
            ?.map
            ?.keys
            ?: emptySet()

        for (workerObjectPath in hostObjectPaths) {
            val workerLocation = ObjectLocation(hostDocument, workerObjectPath)

            val objectMetadata = graphStructure.graphMetadata.get(workerLocation)
                ?: continue

            for ((portName, portMetadata) in objectMetadata.attributes.map) {
                val portType = portMetadata.type
                    ?: continue
                val portKind = JobChannelPorts.kindOf(portType)
                    ?: continue

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
                when (portKind.direction) {
                    JobChannelPorts.Direction.Consumer ->
                        consumers.add(portRef)

                    JobChannelPorts.Direction.Producer ->
                        producers.add(portRef)
                }
            }
        }

        for (port in producers) {
            if (!compatible(port.elementType, declaredElementType)) {
                return mismatch(port, declaredElementType)
            }
        }
        for (port in consumers) {
            if (!compatible(port.elementType, declaredElementType)) {
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
