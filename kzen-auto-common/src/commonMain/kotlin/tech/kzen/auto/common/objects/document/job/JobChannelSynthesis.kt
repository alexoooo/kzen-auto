package tech.kzen.auto.common.objects.document.job

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionIndex
import tech.kzen.lib.common.model.structure.notation.PositionedObjectPath
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * Realizes the order-driven channel model on the RUN path: turns the [JobChannelDerivation] result into real
 * Channel objects + filled Worker-port references in an IN-MEMORY copy of the notation, then re-derives a
 * normal [GraphDefinition] through the existing pipeline. The saved YAML keeps Worker ports blank and (for the
 * common path) carries no Channel objects; only this ephemeral run-copy has them.
 *
 * Why augment-then-redefine rather than synthesize channel instances directly: a Worker with a non-nullable
 * empty channel-port reference is unsatisfiable at `createGraph` time (GraphCreator never schedules it) and
 * [tech.kzen.auto.server.objects.job.JobChannelCreator] independently throws on an empty reference. Filling the
 * references in the run-copy sidesteps both, and because [GraphMetadata] is a pure function of the notation and
 * [GraphDefiner.tryDefine] re-runs the whole definition pipeline (including [ChannelTypeDefiner]),
 * `JobChannelCreator`, channel enumeration, and the migrate carryover all see an ordinary graph and stay
 * unchanged.
 *
 * Synthesized channel identity is deterministic ([JobConventions.autoSynthChannelName] /
 * [JobConventions.autoServeChannelName]), so re-running synthesis each tick / migrate yields the same
 * ObjectLocations — and thus the same migration stable ids — preserving in-flight channel carryover.
 *
 * A Worker's only output that nothing consumes ([JobChannelDerivation.OpenOutput], typically the last Worker's)
 * gets an implicit Preview ([JobConventions.implicitPreviewPath]) inserted after it in the run copy, so the
 * Worker runs and what it produces is sampled for the editor instead of being left unwired.
 */
class JobChannelSynthesis(
    private val notationMetadataReader: NotationMetadataReader
) {
    //-----------------------------------------------------------------------------------------------------------------
    class Result(
        // The augmented FULL graph definition (other documents untouched); the caller filterTransitive's it.
        val graphDefinition: GraphDefinition,

        // Every Channel under the Job document in the augmented notation: synthesized + (later) materialized +
        // manual. The run loop enumerates these to open external clients / index stream channels by stable id.
        val channelLocations: List<ObjectLocation>,

        // The implicit Preview Workers the run copy added ([JobConventions.implicitPreviewPath]): the run launches
        // them beside the saved Workers.
        val implicitWorkers: List<ObjectLocation>
    )


    //-----------------------------------------------------------------------------------------------------------------
    fun synthesize(graphDefinition: GraphDefinition, jobDocumentPath: DocumentPath): Result {
        val savedStructure = graphDefinition.graphStructure
        val implicitPreviews = implicitPreviews(savedStructure, jobDocumentPath)
        val structure = withImplicitPreviews(savedStructure, jobDocumentPath, implicitPreviews)
        val derivation = JobChannelDerivation.derive(structure, jobDocumentPath)

        val documentNotation = structure.graphNotation.documents[jobDocumentPath]

        if (documentNotation == null ||
                (derivation.connections.isEmpty() && derivation.serves.isEmpty())) {
            // No Job doc, or every connection is manually wired: the incoming definition already wires the run.
            return Result(graphDefinition, channelLocationsOf(documentNotation, jobDocumentPath), listOf())
        }

        // Job-wide channel defaults (declared on the Job archetype, so firstAttribute resolves the archetype
        // value even when `main` doesn't override): stamped onto every auto-synthesized channel below. A
        // per-channel override object (kept as-is by ensureChannel) wins; a manual Channel sets its own.
        val mainLocation = ObjectLocation(jobDocumentPath, NotationConventions.mainObjectPath)
        val defaultBatchSize = structure.graphNotation
            .firstAttribute(mainLocation, AttributePath.ofName(JobConventions.batchSizeAttributeName))?.asString()
        val defaultCapacity = structure.graphNotation
            .firstAttribute(mainLocation, AttributePath.ofName(JobConventions.capacityAttributeName))?.asString()

        var augmentedDoc: DocumentNotation = documentNotation
        for (connection in derivation.connections) {
            augmentedDoc = wireOneWay(augmentedDoc, connection, defaultBatchSize, defaultCapacity)
        }
        for (serve in derivation.serves) {
            augmentedDoc = wireServe(augmentedDoc, serve, defaultCapacity)
        }

        return define(structure.graphNotation, jobDocumentPath, augmentedDoc, implicitPreviews.map { it.second })
    }


    /**
     * Completes the ordinary Job synthesis and then gives [objectLocation] a transient channel for each output
     * port that remains open. Detached editor actions use this to instantiate a source Worker before it has a
     * downstream neighbour; saved notation remains unchanged, and a normally connected output keeps the
     * channel selected by [synthesize]. Ports are classified from metadata, so this carries no Worker-name
     * knowledge.
     */
    fun synthesizeOpenOutputsForObject(
        graphDefinition: GraphDefinition,
        objectLocation: ObjectLocation
    ): Result {
        val ordinary = synthesize(graphDefinition, objectLocation.documentPath)
        return withOpenOutputChannels(ordinary, objectLocation.documentPath, listOf(objectLocation.objectPath))
    }


    /**
     * [synthesizeOpenOutputsForObject] for every Worker of the Job: validation uses it so a Worker with several
     * outputs, one of them open, is still instantiated and typed. The run path does not — [synthesize] prunes
     * such a Worker, which would otherwise fill a channel no one drains. (A Worker's only open output never gets
     * this far: [synthesize] hands it to an implicit Preview.)
     */
    fun synthesizeOpenOutputs(
        graphDefinition: GraphDefinition,
        jobDocumentPath: DocumentPath
    ): Result {
        val ordinary = synthesize(graphDefinition, jobDocumentPath)
        val workerPaths = ordinary.graphDefinition.graphStructure.graphNotation.documents[jobDocumentPath]
            ?.directNestedObjectPaths(NotationConventions.mainObjectPath, JobConventions.workersAttributeName)
            ?: return ordinary
        return withOpenOutputChannels(ordinary, jobDocumentPath, workerPaths)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun withOpenOutputChannels(
        ordinary: Result,
        jobDocumentPath: DocumentPath,
        workerPaths: List<ObjectPath>
    ): Result {
        val structure = ordinary.graphDefinition.graphStructure
        val documentNotation = structure.graphNotation.documents[jobDocumentPath]
            ?: return ordinary
        if (!JobConventions.isJob(documentNotation)) {
            return ordinary
        }

        val mainLocation = ObjectLocation(jobDocumentPath, NotationConventions.mainObjectPath)
        val defaultBatchSize = structure.graphNotation
            .firstAttribute(mainLocation, AttributePath.ofName(JobConventions.batchSizeAttributeName))?.asString()
        val defaultCapacity = structure.graphNotation
            .firstAttribute(mainLocation, AttributePath.ofName(JobConventions.capacityAttributeName))?.asString()

        var augmentedDoc = documentNotation
        for (workerPath in workerPaths) {
            val objectLocation = ObjectLocation(jobDocumentPath, workerPath)
            val metadata = structure.graphMetadata.get(objectLocation)
                ?: continue
            val openOutputs = metadata.attributes.map
                .filter { (_, attributeMetadata) ->
                    JobChannelPorts.kindOf(attributeMetadata.type) == JobChannelPorts.Kind.Output
                }
                .map { it.key }
                .filter { outputPort ->
                    isOpenPort(structure.graphNotation, objectLocation, outputPort)
                }

            for (outputPort in openOutputs) {
                val channelName = ObjectName(JobConventions.autoSynthChannelName(workerPath, outputPort))
                val channelObjectPath = channelObjectPath(channelName)
                val channelRef = channelObjectPath.asString()
                val workerNotation = augmentedDoc.objects.notations.map[workerPath]
                val batchSize = workerConfigValue(
                    workerNotation, outputPort, JobConventions.batchSizeAttributeName)
                    ?: defaultBatchSize
                val capacity = workerConfigValue(
                    workerNotation, outputPort, JobConventions.capacityAttributeName)
                    ?: defaultCapacity

                var channelNotation = ObjectNotation.ofParent(JobConventions.channelObjectName)
                channelNotation = upsertIfPresent(
                    channelNotation, JobConventions.batchSizeAttributeName, batchSize)
                channelNotation = upsertIfPresent(
                    channelNotation, JobConventions.capacityAttributeName, capacity)
                augmentedDoc = ensureChannel(augmentedDoc, channelObjectPath, channelNotation)
                augmentedDoc = setPort(augmentedDoc, workerPath, outputPort, channelRef)
            }
        }

        if (augmentedDoc == documentNotation) {
            return ordinary
        }
        return define(structure.graphNotation, jobDocumentPath, augmentedDoc, ordinary.implicitWorkers)
    }


    // Each Worker output nothing consumes, paired with the implicit Preview that will sample it (skipped if the
    // saved document already holds an object at that path).
    private fun implicitPreviews(
        structure: GraphStructure,
        jobDocumentPath: DocumentPath
    ): List<Pair<JobChannelDerivation.OpenOutput, ObjectLocation>> {
        val documentNotation = structure.graphNotation.documents[jobDocumentPath]
            ?: return listOf()
        return JobChannelDerivation.derive(structure, jobDocumentPath)
            .openOutputs
            .map { it to JobConventions.implicitPreviewPath(it.worker.objectPath, it.outputPort) }
            .filter { (_, previewPath) -> documentNotation.objects.notations.map[previewPath] == null }
            .map { (openOutput, previewPath) -> openOutput to ObjectLocation(jobDocumentPath, previewPath) }
    }


    // Inserts each implicit Preview directly after the Worker whose output it samples, so the ordinary
    // order-driven derivation then wires the two (and the Preview's `serve`) like any adjacent pair — no knowledge
    // of the Preview's port names here.
    private fun withImplicitPreviews(
        structure: GraphStructure,
        jobDocumentPath: DocumentPath,
        implicitPreviews: List<Pair<JobChannelDerivation.OpenOutput, ObjectLocation>>
    ): GraphStructure {
        if (implicitPreviews.isEmpty()) {
            return structure
        }
        var documentNotation = structure.graphNotation.documents[jobDocumentPath]
            ?: return structure
        for ((openOutput, previewLocation) in implicitPreviews) {
            val upstreamIndex = documentNotation.indexOf(openOutput.worker.objectPath).value
            documentNotation = documentNotation.withNewObject(
                PositionedObjectPath(previewLocation.objectPath, PositionIndex(upstreamIndex + 1)),
                ObjectNotation.ofParent(JobConventions.implicitPreviewObjectName))
        }
        val notation = structure.graphNotation.withModifiedDocument(jobDocumentPath, documentNotation)
        return GraphStructure(notation, notationMetadataReader.read(notation))
    }


    // Re-runs the whole definition pipeline over the augmented Job document. Transitively successful, as the
    // incoming definition is: a Worker whose required port is still blank (an output that an implicit Preview
    // does not take, such as one of several) is pruned rather than left to fail `filterTransitive` on its
    // missing reference.
    private fun define(
        graphNotation: GraphNotation,
        jobDocumentPath: DocumentPath,
        augmentedDoc: DocumentNotation,
        implicitWorkers: List<ObjectLocation>
    ): Result {
        val augmentedNotation = graphNotation.withModifiedDocument(jobDocumentPath, augmentedDoc)
        val augmentedStructure = GraphStructure(
            augmentedNotation, notationMetadataReader.read(augmentedNotation))
        val augmentedDefinition = GraphDefiner.tryDefine(augmentedStructure).transitiveSuccessful
        return Result(augmentedDefinition, channelLocationsOf(augmentedDoc, jobDocumentPath), implicitWorkers)
    }


    private fun channelLocationsOf(
        documentNotation: DocumentNotation?,
        jobDocumentPath: DocumentPath
    ): List<ObjectLocation> {
        documentNotation ?: return listOf()
        return documentNotation
            .directNestedObjectPaths(NotationConventions.mainObjectPath, JobConventions.channelsAttributeName)
            .map { ObjectLocation(jobDocumentPath, it) }
    }


    private fun wireOneWay(
        documentNotation: DocumentNotation,
        connection: JobChannelDerivation.Connection,
        defaultBatchSize: String?,
        defaultCapacity: String?
    ): DocumentNotation {
        val channelName = ObjectName(JobConventions.autoSynthChannelName(
            connection.upstreamWorker.objectPath, connection.outputPort))
        val channelObjectPath = channelObjectPath(channelName)
        val channelRef = channelObjectPath.asString()

        // Per-output config lives on the upstream Worker in its `channels.<outputPort>` map (absent = inherit):
        // its own non-blank value wins, else the Job-wide default. Living on the Worker, it follows the Worker
        // across rename / reorder — the channel object carries no name-coupled override. Keyed by output port so
        // a Worker can tune each of its output channels independently.
        val upstreamNotation = documentNotation.objects.notations.map[connection.upstreamWorker.objectPath]
        val batchSize = workerConfigValue(
            upstreamNotation, connection.outputPort, JobConventions.batchSizeAttributeName)
            ?: defaultBatchSize
        val capacity = workerConfigValue(
            upstreamNotation, connection.outputPort, JobConventions.capacityAttributeName)
            ?: defaultCapacity

        var channelNotation = ObjectNotation.ofParent(JobConventions.channelObjectName)
        channelNotation = upsertIfPresent(channelNotation, JobConventions.batchSizeAttributeName, batchSize)
        channelNotation = upsertIfPresent(channelNotation, JobConventions.capacityAttributeName, capacity)

        var result = ensureChannel(documentNotation, channelObjectPath, channelNotation)
        result = setPort(result, connection.upstreamWorker.objectPath, connection.outputPort, channelRef)
        result = setPort(result, connection.downstreamWorker.objectPath, connection.inputPort, channelRef)
        return result
    }


    // The Worker's OWN `channels.<outputPort>.<knob>` value, or null when unset / blank so the caller falls back
    // to the Job-wide default. Read from the Worker's own notation (not inheritance-resolved — the Worker
    // archetype declares no default map).
    private fun workerConfigValue(
        workerNotation: ObjectNotation?,
        outputPort: AttributeName,
        knob: AttributeName
    ): String? {
        return workerNotation
            ?.get(JobConventions.workerOutputKnobPath(outputPort, knob))
            ?.asString()
            ?.ifBlank { null }
    }


    private fun wireServe(
        documentNotation: DocumentNotation,
        serve: JobChannelDerivation.Serve,
        defaultCapacity: String?
    ): DocumentNotation {
        val channelName = ObjectName(JobConventions.autoServeChannelName(serve.worker.objectPath))
        val channelObjectPath = channelObjectPath(channelName)
        val channelRef = channelObjectPath.asString()

        // Duplex channels have no batchSize (not batched) — only capacity applies.
        var duplexNotation = ObjectNotation
            .ofParent(JobConventions.duplexChannelObjectName)
            .upsertAttribute(JobConventions.externalAttributeName, ScalarAttributeNotation("true"))
        duplexNotation = upsertIfPresent(duplexNotation, JobConventions.capacityAttributeName, defaultCapacity)

        var result = ensureChannel(documentNotation, channelObjectPath, duplexNotation)
        result = setPort(result, serve.worker.objectPath, serve.servePort, channelRef)
        return result
    }


    private fun upsertIfPresent(
        objectNotation: ObjectNotation,
        attributeName: AttributeName,
        value: String?
    ): ObjectNotation {
        value ?: return objectNotation
        return objectNotation.upsertAttribute(attributeName, ScalarAttributeNotation(value))
    }


    private fun channelObjectPath(channelName: ObjectName): ObjectPath {
        return NotationConventions.mainObjectPath.nest(JobConventions.channelsAttributePath, channelName)
    }


    // Append the synthesized Channel if absent (idempotent — re-derive on migrate yields the same name);
    // position is cosmetic for the run, so append at the end of the document.
    private fun ensureChannel(
        documentNotation: DocumentNotation,
        channelObjectPath: ObjectPath,
        channelNotation: ObjectNotation
    ): DocumentNotation {
        if (documentNotation.objects.notations.map[channelObjectPath] != null) {
            return documentNotation
        }
        return documentNotation.withNewObject(
            PositionedObjectPath(channelObjectPath, PositionIndex(documentNotation.objects.notations.map.size)),
            channelNotation)
    }


    private fun setPort(
        documentNotation: DocumentNotation,
        workerPath: ObjectPath,
        port: AttributeName,
        channelRef: String
    ): DocumentNotation {
        val workerNotation = documentNotation.objects.notations.map[workerPath]
            ?: return documentNotation
        return documentNotation.withModifiedObject(
            workerPath, workerNotation.upsertAttribute(port, ScalarAttributeNotation(channelRef)))
    }


    private fun isOpenPort(
        graphNotation: GraphNotation,
        objectLocation: ObjectLocation,
        portName: AttributeName
    ): Boolean {
        val value = graphNotation
            .firstAttribute(objectLocation, AttributePath.ofName(portName))
            ?.asString()
        if (value.isNullOrBlank()) {
            return true
        }
        val referenced = graphNotation.coalesce.locateOptional(
            tech.kzen.lib.common.model.location.ObjectReference.parse(value),
            tech.kzen.lib.common.model.location.ObjectReferenceHost.ofLocation(objectLocation))
        return referenced == null
    }
}
