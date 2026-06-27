package tech.kzen.auto.common.objects.document.job

import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Conventions for the Job document — a graph of concurrently-running Workers connected by named Channels.
 * Mirrors [tech.kzen.auto.common.objects.document.flow.FlowConventions]: `workers` / `channels` autowire
 * from nested document structure (like Script's `steps`). A Worker connects to a Channel by carrying a
 * reference to it in a channel-typed attribute; those attributes are arbitrarily named and resolved by
 * TYPE (ChannelInput / ChannelOutput), so there is no fixed `in` / `out` naming convention to encode here.
 */
object JobConventions {
    val objectName = ObjectName("Job")
    val channelObjectName = ObjectName("Channel")
    val duplexChannelObjectName = ObjectName("DuplexChannel")

    val workersAttributeName = AttributeName("workers")
    val workersAttributePath = AttributePath.ofName(workersAttributeName)

    val channelsAttributeName = AttributeName("channels")
    val channelsAttributePath = AttributePath.ofName(channelsAttributeName)

    // Marks a duplex Channel whose client side is the UI bridge rather than a Worker (see JobExecution).
    val externalAttributeName = AttributeName("external")

    // Request-protocol param naming which `external` Channel an inbound ExecutionRequest targets (the UI ->
    // Worker bridge addresses a Job's external duplex channels by their leaf object name). See JobExecution.
    const val channelParameter = "channel"

    // The single free-form query value the built-in minimal Job UI sends to an external channel. Purely the
    // minimal UI's convention — a richer worker protocol defines (and the UI sends) its own params.
    const val requestParameter = "request"

    // Leading segment of a Worker's live-progress trace path (push path: counts / preview sample). This is a
    // FIXED-convention path, NOT a `$stable` object path: the trace store resolves every `$stable` path's
    // stable id back to an ObjectLocation and drops it if that fails, so a `$stable` path with an extra
    // segment (…/<worker>/progress) would be silently dropped from snapshots. A fixed-convention path is
    // retained as-is (cf. the Report's output-count trace path). Kept distinct from the bare `$stable` path
    // where JobExecution writes the terminal status, so the two never collide.
    private const val progressTraceSegment = "jobWorkerProgress"

    // The live-progress path for the Worker identified by [objectStableId]; shared by the server publish
    // (JobControlImpl) and the client read (JobProgressStore). Encodes the stable id so each Worker has its
    // own entry; split on the separator so each piece satisfies LogicTracePath's no-separator invariant.
    fun workerProgressPath(objectStableId: ObjectStableId): LogicTracePath {
        return LogicTracePath(
            listOf(progressTraceSegment) +
                objectStableId.value.split(LogicTracePath.segmentSeparator))
    }

    // Slice-query params the PreviewWorker's duplex `serve` channel understands (pull path): which window of
    // its buffered sample to return. Shared by the server (PreviewWorker) and the client (JobController).
    const val previewOffsetParameter = "offset"
    const val previewLimitParameter = "limit"


    // Deterministic name for the one-way channel auto-synthesized to carry one adjacent-Worker connection
    // (order-driven wiring — see [JobChannelSynthesis]): `ch__<upstreamWorkerLeaf>__<outputPort>`. A pure
    // function of the upstream Worker's leaf name and its output port, so the synthesized channel's
    // ObjectLocation — and thus its migration stable id (ObjectStableMapper keys on the location string) — is
    // stable across edits that don't rename the upstream Worker, preserving in-flight carryover across a
    // pause / edit / resume. Mirrored by the JS editor when it renders a synthesized pipe.
    fun autoSynthChannelName(upstreamWorker: ObjectPath, outputPort: AttributeName): String {
        return "ch__${upstreamWorker.name.value}__${outputPort.value}"
    }


    // Deterministic name for the external duplex channel auto-synthesized for a Worker's UI `serve` port:
    // `ch__<workerLeaf>__serve`. JobExecution keys its external client by this leaf name (route by
    // [channelParameter]); the JS client addresses the same name when it pulls a larger preview slice (see
    // JobController.queryPreviewSlice). One serve channel per Worker, so the fixed `serve` suffix is unique.
    fun autoServeChannelName(worker: ObjectPath): String {
        return "ch__${worker.name.value}__serve"
    }


    fun isJob(documentNotation: DocumentNotation): Boolean {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
                ?: return false

        val mainObjectIs =
            mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
                ?: return false

        return mainObjectIs == objectName.value
    }


    fun isExternalChannel(documentNotation: DocumentNotation, channelObjectPath: ObjectPath): Boolean {
        return documentNotation.objects.notations[channelObjectPath]
            ?.get(externalAttributeName)
            ?.asBoolean()
            ?: false
    }


    // True when the given archetype is a Channel (one-way or duplex) rather than a Worker — i.e. its
    // inheritance chain reaches the Channel / DuplexChannel base. Used by the editor to decide whether a
    // ribbon-inserted object nests under `channels` or `workers`.
    fun isChannelArchetype(graphNotation: GraphNotation, archetypeLocation: ObjectLocation): Boolean {
        return graphNotation.inheritanceChain(archetypeLocation).any {
            val name = it.objectPath.name
            name == channelObjectName || name == duplexChannelObjectName
        }
    }
}
