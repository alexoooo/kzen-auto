package tech.kzen.auto.common.objects.document.job

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * The order-driven channel-wiring rule, as a PURE function of the saved structure — no notation mutation, no
 * re-definition. Worker document order IS the pipeline: an adjacent pair (A above, B below) is auto-connected
 * when A has exactly one OPEN ([isOpenPort]) output port and B exactly one open input port. An open Worker
 * `serve` (ChannelServer) port yields a UI-facing duplex connection.
 *
 * A non-blank port is a MANUAL wire (the fan-in / branch / non-linear escape hatch) and is excluded from
 * pairing — it is already validated + wired by the existing reference machinery
 * ([ChannelTypeDefiner] / [tech.kzen.auto.server.objects.job.JobChannelCreator]).
 *
 * Both sides call this: the server's [JobChannelSynthesis] turns the result into real channels + filled
 * references for the run, and the JS editor (JobController) turns it into the gold pipes drawn between Worker
 * cards. Keeping the rule here means the two cannot drift.
 */
object JobChannelDerivation {
    //-----------------------------------------------------------------------------------------------------------------
    // One inferred one-way channel between two adjacent Workers (upstream output -> downstream input).
    data class Connection(
        val upstreamWorker: ObjectLocation,
        val outputPort: AttributeName,
        val downstreamWorker: ObjectLocation,
        val inputPort: AttributeName
    )


    // A Worker's open ChannelServer (UI-facing) port, for which an external duplex channel is auto-managed.
    data class Serve(
        val worker: ObjectLocation,
        val servePort: AttributeName
    )


    // A Worker's single open output port that no adjacent Worker consumes (typically the last Worker's): the editor
    // still draws its outgoing pipe, so the output channel and its type are visible before a consumer is inserted.
    data class OpenOutput(
        val worker: ObjectLocation,
        val outputPort: AttributeName
    )


    data class Result(
        val connections: List<Connection>,
        val serves: List<Serve>,
        val openOutputs: List<OpenOutput>
    ) {
        companion object {
            val empty = Result(listOf(), listOf(), listOf())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private class WorkerPorts(
        val location: ObjectLocation,
        val openInputs: List<AttributeName>,
        val openOutputs: List<AttributeName>,
        val openServes: List<AttributeName>
    )


    //-----------------------------------------------------------------------------------------------------------------
    fun derive(graphStructure: GraphStructure, jobDocumentPath: DocumentPath): Result {
        val graphNotation = graphStructure.graphNotation
        val documentNotation = graphNotation.documents[jobDocumentPath]
            ?: return Result.empty
        if (!JobConventions.isJob(documentNotation)) {
            return Result.empty
        }

        val workers = documentNotation
            .directNestedObjectPaths(NotationConventions.mainObjectPath, JobConventions.workersAttributeName)
            .map { workerPath ->
                val workerLocation = ObjectLocation(jobDocumentPath, workerPath)
                readWorkerPorts(workerLocation, graphStructure)
            }

        // Linear auto-wire: pair adjacent Workers only when the connection is unambiguous (exactly one open
        // output above, exactly one open input below). 0 or >1 open ports of a kind -> skip (the Worker needs
        // a manual channel — a non-linear topology, deferred).
        val connections = mutableListOf<Connection>()
        for (i in 0 until workers.size - 1) {
            val upstream = workers[i]
            val downstream = workers[i + 1]
            if (upstream.openOutputs.size == 1 && downstream.openInputs.size == 1) {
                connections.add(Connection(
                    upstream.location, upstream.openOutputs.single(),
                    downstream.location, downstream.openInputs.single()))
            }
        }

        val serves = workers.flatMap { worker ->
            worker.openServes.map { Serve(worker.location, it) }
        }

        val connectedUpstreams = connections.map { it.upstreamWorker }.toSet()
        val openOutputs = workers
            .filter { it.openOutputs.size == 1 && it.location !in connectedUpstreams }
            .map { OpenOutput(it.location, it.openOutputs.single()) }

        return Result(connections, serves, openOutputs)
    }


    /**
     * The Worker that drains [producer]'s output, whichever way the two are joined: by the order rule above, or by
     * a manual wire to one shared Channel (which is also how every pair appears in a synthesized run-copy).
     * Null when nothing consumes it, or when the producer has several outputs.
     */
    fun consumerOf(graphStructure: GraphStructure, producer: ObjectLocation): ObjectLocation? {
        val derived = derive(graphStructure, producer.documentPath)
            .connections
            .firstOrNull { it.upstreamWorker == producer }
        if (derived != null) {
            return derived.downstreamWorker
        }

        val channel = wiredChannels(graphStructure, producer, JobChannelPorts.Kind.Output).singleOrNull()
            ?: return null
        val documentNotation = graphStructure.graphNotation.documents[producer.documentPath]
            ?: return null
        return documentNotation
            .directNestedObjectPaths(NotationConventions.mainObjectPath, JobConventions.workersAttributeName)
            .map { ObjectLocation(producer.documentPath, it) }
            .firstOrNull { it != producer && channel in wiredChannels(graphStructure, it, JobChannelPorts.Kind.Input) }
    }


    private fun wiredChannels(
        graphStructure: GraphStructure,
        workerLocation: ObjectLocation,
        kind: JobChannelPorts.Kind
    ): List<ObjectLocation> {
        val metadata = graphStructure.graphMetadata.get(workerLocation)
            ?: return listOf()
        return metadata.attributes.map
            .filter { JobChannelPorts.kindOf(it.value.type) == kind }
            .mapNotNull { (attributeName, _) ->
                graphStructure.graphNotation
                    .firstAttribute(workerLocation, AttributePath.ofName(attributeName))
                    ?.asString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        graphStructure.graphNotation.coalesce.locateOptional(
                            ObjectReference.parse(it), ObjectReferenceHost.ofLocation(workerLocation))
                    }
            }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun readWorkerPorts(workerLocation: ObjectLocation, graphStructure: GraphStructure): WorkerPorts {
        val openInputs = mutableListOf<AttributeName>()
        val openOutputs = mutableListOf<AttributeName>()
        val openServes = mutableListOf<AttributeName>()

        val metadata = graphStructure.graphMetadata.get(workerLocation)
        if (metadata != null) {
            for ((attributeName, attributeMetadata) in metadata.attributes.map) {
                val kind = JobChannelPorts.kindOf(attributeMetadata.type)
                    ?: continue
                if (!isOpenPort(graphStructure.graphNotation, workerLocation, attributeName)) {
                    continue
                }
                when (kind) {
                    JobChannelPorts.Kind.Input -> openInputs.add(attributeName)
                    JobChannelPorts.Kind.Output -> openOutputs.add(attributeName)
                    JobChannelPorts.Kind.Server -> openServes.add(attributeName)
                    // An open ChannelClient has no producer-side to pair with; not auto-managed (manual only).
                    JobChannelPorts.Kind.Client -> {}
                }
            }
        }

        return WorkerPorts(workerLocation, openInputs, openOutputs, openServes)
    }


    // A channel port is OPEN when the order rule owns it: its (inheritance-resolved) notation scalar is blank
    // (the archetype defaults it to ""), OR it is a DANGLING reference to a Channel that does not exist. A
    // non-blank port is a MANUAL wire ONLY when it resolves to a real Channel object; a leftover reference whose
    // Channel was removed (e.g. a `serve:`/`output:` orphaned by a hand-edit — and which the editor now hides,
    // so the user cannot clear it) is not a valid manual wire. Reclaiming it lets the run-copy re-point the port
    // to a synthesized Channel instead of crashing `GraphDefinition.filterTransitive` on the missing object.
    private fun isOpenPort(
        graphNotation: GraphNotation,
        workerLocation: ObjectLocation,
        portName: AttributeName
    ): Boolean {
        val value = graphNotation
            .firstAttribute(workerLocation, AttributePath.ofName(portName))
            ?.asString()
        if (value.isNullOrBlank()) {
            return true
        }
        val referenced = graphNotation.coalesce.locateOptional(
            ObjectReference.parse(value),
            ObjectReferenceHost.ofLocation(workerLocation))
        return referenced == null
    }
}
