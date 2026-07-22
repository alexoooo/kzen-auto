package tech.kzen.auto.common.objects.document.job

import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
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
    // The server-side validation pass (payload-type walk) the editor queries as a detached action —
    // the ScriptConventions.scriptValidatorLocation analogue.
    val jobValidatorLocation = ObjectLocation.parse(
        "auto-jvm/job/job-jvm.yaml#JobValidator")

    val objectName = ObjectName("Job")
    val channelObjectName = ObjectName("Channel")
    val duplexChannelObjectName = ObjectName("DuplexChannel")

    // Semantic ChannelServer subtype (declared in common-job.yaml, no own `class:` so it resolves to the
    // ChannelServer class). A Worker declares its `serve` port as this to mark itself a summary source;
    // JobServeCapability classifies by inheritance-chain membership, so subtypes are recognized (see CC-17).
    val summaryServerObjectName = ObjectName("SummaryServer")

    // Signature marker archetype (common-job.yaml, the SummaryServer pattern): a Worker whose inheritance chain
    // reaches ResultSink yields one of the Job's declared output components. JobSignatureCapability classifies
    // by chain membership, so subtypes are recognized (CC-17). The signature itself is declared on the document:
    // outputs from the `results` map, inputs from the `parameters` branch of typed ParameterBinding
    // declarations (LogicConventions).
    val resultSinkObjectName = ObjectName("ResultSink")

    // The marker-declared name attribute: a ResultSink's `result` names the declared output component it
    // yields into (blank = main). Signature-managed — hidden from the Worker card's editors.
    val resultAttributeName = AttributeName("result")

    val workersAttributeName = AttributeName("workers")
    val workersAttributePath = AttributePath.ofName(workersAttributeName)

    val channelsAttributeName = AttributeName("channels")
    val channelsAttributePath = AttributePath.ofName(channelsAttributeName)

    // A Channel's two Int knobs (see job-jvm.yaml Channel): `batchSize` = elements grouped into one physical
    // transfer batch (the checkpoint / step unit); `capacity` = how many batches the channel holds before
    // backpressure (0 = rendezvous handoff). Three roles: the flat Job-wide default on the Job archetype
    // (`main`); the leaf-segment names inside a Worker's per-output `channels` map (see below); and the flat
    // stamp targets on the synthesized Channel object.
    val batchSizeAttributeName = AttributeName("batchSize")
    val capacityAttributeName = AttributeName("capacity")

    // A Worker's per-output channel config: a free-form map keyed by output-port name, each value a map of the
    // knobs above — `channels: { <port>: { batchSize, capacity } }`. Reused string "channels" is distinct from
    // [channelsAttributeName] above (that nests the Job DOCUMENT's Channel OBJECTS under `main`; this is plain
    // config data on a WORKER — different object, different mechanism). Undeclared in the Worker's `meta` on
    // purpose (a map infers to no metadata → no card editor, no definition, no "Missing" drop), so there is no
    // constant pair for the map's own metadata; only these path builders address it.
    val workerChannelsAttributeName = AttributeName("channels")

    // `channels.<port>` — the config container for one output port (used to create / clear the whole entry).
    fun workerOutputConfigPath(outputPort: AttributeName): AttributePath {
        return AttributePath.ofName(workerChannelsAttributeName)
            .nest(AttributeSegment.ofKey(outputPort.value))
    }

    // `channels.<port>.<knob>` — a single knob leaf for one output port (read by synthesis, edited by the field).
    fun workerOutputKnobPath(outputPort: AttributeName, knob: AttributeName): AttributePath {
        return workerOutputConfigPath(outputPort)
            .nest(AttributeSegment.ofKey(knob.value))
    }

    // Marks a duplex Channel whose client side is the UI bridge rather than a Worker (see JobRun).
    val externalAttributeName = AttributeName("external")

    // Request-protocol param naming which `external` Channel an inbound ExecutionRequest targets (the UI ->
    // Worker bridge addresses a Job's external duplex channels by their leaf object name). See JobRun.
    const val channelParameter = "channel"

    // The single free-form query value the built-in minimal Job UI sends to an external channel. Purely the
    // minimal UI's convention — a richer worker protocol defines (and the UI sends) its own params.
    const val requestParameter = "request"

    // Leading segment of a Worker's live-progress trace path (push path: counts / preview sample). This is a
    // FIXED-convention path, NOT a `$stable` object path: the trace store resolves every `$stable` path's
    // stable id back to an ObjectLocation and drops it if that fails, so a `$stable` path with an extra
    // segment (…/<worker>/progress) would be silently dropped from snapshots. A fixed-convention path is
    // retained as-is (cf. the Report's output-count trace path). Kept distinct from the bare `$stable` path
    // where the Worker's own stable-id (status) path lives, so the two never collide.
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

    // Well-known keys of the otherwise-opaque worker progress map, shared by the built-in workers (JVM) and
    // their displays (JS) so the wire contract can't drift. Third-party workers may use any keys with their
    // own displays.
    const val progressCountKey = "count"
    const val progressHeaderKey = "header"
    const val progressRowsKey = "rows"
    const val progressSummaryKey = "summary"

    // The kept Result value's display text, pushed by ResultSinkWorker for ResultWorkerDisplay's value box.
    // A single-element List (not a bare scalar) so the generic default-card status line skips it — only the
    // per-type display renders it (the progressRowsKey / progressSummaryKey precedent).
    const val progressResultValueKey = "resultValue"

    // Max rows a non-forced (periodic) progress push may carry: push is a teaser, pull is the payload — every
    // emit is retained in engine history, so periodic pushes must be O(bounded).
    const val progressTeaserRowCount = 10


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
    // `ch__<workerLeaf>__serve`. JobRun keys its external client by this leaf name (route by
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
