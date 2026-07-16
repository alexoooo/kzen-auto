package tech.kzen.auto.server.exec

import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.BinaryHandleExecutionValue
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Node
import tech.kzen.lib.common.exec.engine.NodeId
import tech.kzen.lib.common.exec.engine.NodeStatus
import tech.kzen.lib.common.exec.engine.OutcomeTrace
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionInfo
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.trace.LogicTrace
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceEntry
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceEvent
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.time.Clock


/**
 * Read handle onto the current / most-recently-settled run's [RunEngine], for the trace-query surface.
 * [ServerLogicController] hands one out (whether the run is active or terminal-retained) so the trace view
 * can project it without holding the controller's lock across the query.
 */
class RunTraceAccess(
    val runId: LogicRunId,
    val engine: RunEngine
)


/**
 * Serves the [LogicTrace] REST surface by **projecting the retained [RunEngine]** at query time, so there is
 * no second trace store to keep in step (the former `LogicTraceStore` bridge is retired — logic-spec §7). The
 * engine already holds everything the store duplicated: the node tree (execution tree + `mostRecent` +
 * traced-documents), each node's live latest-value map with its `liveSequence` (`lookup` / `lookupRun`), and
 * the append-only event log (`lookupRunHistory`).
 *
 * The per-flavour within-node [Address] → wire [LogicTracePath] translation the bridge used to apply at WRITE
 * time now happens here at QUERY time, from the same autowired [LogicTraceAddressRouting] set (a reserved
 * marker segment routes per-flavour — Job worker progress, Report input/output — else the leading segment IS
 * the element stable id). Rename survival: paths stay `ObjectStableId`-keyed and are resolved to the current
 * [ObjectLocation] via [ObjectStableMapper] (dropped once the object is deleted), exactly as the store did.
 *
 * [activeRun] returns the run to project (null when no run has started this process life); [clearRetained]
 * disposes it (the "Clear all traces" action) — both from the controller.
 */
class RunEngineLogicTrace(
    private val objectStableMapper: ObjectStableMapper,
    traceAddressRoutings: List<LogicTraceAddressRouting>,
    private val activeRun: () -> RunTraceAccess?,
    private val clearRetained: () -> Boolean
):
    LogicTrace
{
    //-----------------------------------------------------------------------------------------------------------------
    // Indexed by reserved marker, mirroring the former ServerLogicController.tracePathOf; names no flavour.
    private val routingByMarker: Map<String, LogicTraceAddressRouting> =
        traceAddressRoutings.associateBy { it.marker }


    //-----------------------------------------------------------------------------------------------------------------
    override fun mostRecent(objectLocation: ObjectLocation): LogicRunExecutionId? {
        val access = activeRun()
            ?: return null
        val stableId = objectStableMapper.objectStableId(objectLocation)
        val match = mostRecentNode(access.engine.snapshot().root, stableId)
            ?: return null
        return LogicRunExecutionId(access.runId, LogicExecutionId(match.id.value))
    }


    override fun tracedLocations(): Set<ObjectLocation> {
        val access = activeRun()
            ?: return emptySet()
        val result = mutableSetOf<ObjectLocation>()
        forEachNode(access.engine.snapshot().root) { node ->
            try {
                result.add(objectStableMapper.objectLocation(node.stableId))
            }
            catch (_: IllegalArgumentException) {
                // Document deleted since the run — its trace can't be addressed; skip it.
            }
        }
        return result
    }


    override fun clear(objectLocation: ObjectLocation): Boolean {
        // Per-document clear isn't used by the client; treat it as the global clear only when it targets the
        // retained run's root (kept for wire-compat with actionReset), else no-op.
        val access = activeRun()
            ?: return false
        val stableId = objectStableMapper.objectStableId(objectLocation)
        if (access.engine.snapshot().root.stableId == stableId) {
            return clearRetained()
        }
        return false
    }


    override fun clearAll() {
        clearRetained()
    }


    override fun lookup(
        logicRunExecutionId: LogicRunExecutionId,
        logicTraceQuery: LogicTraceQuery
    ): LogicTraceSnapshot? {
        val access = activeRun()
            ?: return null
        if (access.runId != logicRunExecutionId.logicRunId) {
            return null
        }
        val nodeId = NodeId(logicRunExecutionId.logicExecutionId.value)
        val node = findNode(access.engine.snapshot().root, nodeId)
            ?: return null

        val time = Clock.System.now()
        return LogicTraceSnapshot(filterAndRetain(nodeEntries(node, time, access.runId), logicTraceQuery))
    }


    override fun lookupRun(
        logicRunId: LogicRunId,
        logicTraceQuery: LogicTraceQuery
    ): LogicTraceSnapshot? {
        val access = activeRun()
            ?: return null
        if (access.runId != logicRunId) {
            return null
        }

        // Whole-run merge: keep only the LATEST node per stable id (highest node ordinal), then latest
        // sequence wins on any residual path collision. This reproduces the former store's re-entry
        // clearing generically — a superseded invocation of a hosted sub-logic (a loop iteration, or a
        // second RunStep invoking the same document) drops out of the merged live view, while its
        // append-only history (the film strip) survives via lookupRunHistory.
        val latestByStableId = HashMap<ObjectStableId, Node>()
        forEachNode(access.engine.snapshot().root) { node ->
            val previous = latestByStableId[node.stableId]
            if (previous == null || nodeOrdinal(node.id) > nodeOrdinal(previous.id)) {
                latestByStableId[node.stableId] = node
            }
        }

        val time = Clock.System.now()
        val merged = LinkedHashMap<LogicTracePath, LogicTraceEntry>()
        for (node in latestByStableId.values) {
            for ((path, entry) in nodeEntries(node, time, access.runId)) {
                val existing = merged[path]
                if (existing == null || entry.sequence > existing.sequence) {
                    merged[path] = entry
                }
            }
        }
        return LogicTraceSnapshot(filterAndRetain(merged, logicTraceQuery))
    }


    override fun lookupRunHistory(
        logicRunId: LogicRunId,
        sinceSequence: Long
    ): List<LogicTraceEvent> {
        val access = activeRun()
            ?: return emptyList()
        if (access.runId != logicRunId) {
            return emptyList()
        }

        // The append-only film-strip is exactly the log-style events (null address); emit-style events are
        // the live values, served by lookup / lookupRun. history() is already sequence-ordered (single
        // writer) and incremental (> sinceSequence). Time is synthesized (the engine event log carries no
        // wall clock; the client orders by sequence, never time).
        val time = Clock.System.now()
        return access.engine.history(sinceSequence)
            .filter { it.address == null }
            .map { event ->
                LogicTraceEvent(
                    LogicExecutionId(event.nodeId.value),
                    event.stableId,
                    event.stableId,
                    event.sequence,
                    time,
                    toWireValue(event.value, access.runId))
            }
    }


    /**
     * Resolve the raw bytes of a binary trace value by its content hash (`Digest.ofBytes(bytes).asString()`),
     * for the `/logic/trace-binary` blob endpoint. Scans the union of every node's live map and the
     * append-only history: a screenshot can be a live emit (served by [lookup] / [lookupRun]) and/or a
     * retained log event (the film strip, which survives a loop's `clearAll` of the live paths). Returns null
     * (→ 404) if the requested run isn't the retained one, or no retained binary hashes to [hash].
     */
    fun lookupBinary(logicRunId: LogicRunId, hash: String): ByteArray? {
        val access = activeRun()
            ?: return null
        if (access.runId != logicRunId) {
            return null
        }

        var match: ByteArray? = null
        forEachNode(access.engine.snapshot().root) { node ->
            if (match == null) {
                for (value in node.live.values) {
                    if (value is BinaryExecutionValue && Digest.ofBytes(value.value).asString() == hash) {
                        match = value.value
                        break
                    }
                }
            }
        }
        if (match != null) {
            return match
        }

        for (event in access.engine.history(0L)) {
            val value = event.value
            if (value is BinaryExecutionValue && Digest.ofBytes(value.value).asString() == hash) {
                return value.value
            }
        }
        return null
    }


    override fun lookupRunExecutions(
        logicRunId: LogicRunId
    ): List<LogicRunExecutionInfo> {
        val access = activeRun()
            ?: return emptyList()
        if (access.runId != logicRunId) {
            return emptyList()
        }

        val result = mutableListOf<LogicRunExecutionInfo>()
        fun visit(node: Node, parentId: NodeId?) {
            result.add(LogicRunExecutionInfo(
                LogicExecutionId(node.id.value),
                parentId?.let { LogicExecutionId(it.value) },
                node.callerStableId))
            node.children.forEach { visit(it, node.id) }
        }
        visit(access.engine.snapshot().root, null)
        return result
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Replace a large inline binary trace value with a content-addressed handle: the wire JSON carries a hash
    // instead of ~1 MB of base64 per screenshot, and the bytes are served once, out-of-band, by the
    // /logic/trace-binary blob endpoint (resolved by lookupBinary). General to ANY binary trace value — not
    // screenshot-specific; the "image/png" mime is the trace-binary default (every trace binary is a screenshot
    // today) and is informational only (the blob is served as octet-stream; the browser sniffs). Non-binary
    // values pass through unchanged, so this is the single seam that scopes handles to the trace wire — a
    // detached ScreenshotTaker result never passes through here and keeps its inline base64.
    private fun toWireValue(value: ExecutionValue, runId: LogicRunId): ExecutionValue {
        if (value !is BinaryExecutionValue) {
            return value
        }
        return BinaryHandleExecutionValue(
            runId.value,
            Digest.ofBytes(value.value).asString(),
            value.value.size,
            "image/png")
    }


    // A single node's live map, translated to wire paths with each entry's live sequence.
    private fun nodeEntries(
        node: Node,
        time: kotlin.time.Instant,
        runId: LogicRunId
    ): Map<LogicTracePath, LogicTraceEntry> {
        val result = LinkedHashMap<LogicTracePath, LogicTraceEntry>()
        for ((address, value) in node.live) {
            val path = tracePathOf(address, node.stableId)
            val sequence = node.liveSequence[address] ?: 0L
            val existing = result[path]
            if (existing == null || sequence > existing.sequence) {
                result[path] = LogicTraceEntry(toWireValue(value, runId), time, sequence)
            }
        }

        // A settled node's terminal outcome, projected onto a flavour-neutral dedicated path (see
        // LogicTracePath.nodeOutcome). Read-time synthesis from node.status — not an emit, so no address
        // routing is involved and the path is unique per node (never collides with a live value). Survives the
        // run via the retained engine, so the Job UI reads each Worker's outcome to render its chip after the
        // run ends (and the root node's entry carries the whole-run outcome). Emitted for every terminal node
        // of every flavour; flavours that don't read the outcome path are unaffected (kept unconditional to
        // avoid a flavour branch in this generic projector).
        val terminal = node.status as? NodeStatus.Terminal
        if (terminal != null) {
            val sequence = node.liveSequence.values.maxOrNull() ?: 0L
            result[LogicTracePath.nodeOutcome(node.stableId)] =
                LogicTraceEntry(ExecutionValue.of(OutcomeTrace.toMap(terminal.outcome)), time, sequence)
        }
        return result
    }


    // Query filter + rename resolution: keep the stable key (the client resolves it to the current
    // location), but drop entries whose object no longer exists.
    private fun filterAndRetain(
        entries: Map<LogicTracePath, LogicTraceEntry>,
        query: LogicTraceQuery
    ): Map<LogicTracePath, LogicTraceEntry> {
        val result = LinkedHashMap<LogicTracePath, LogicTraceEntry>()
        for ((path, entry) in entries) {
            val retained = retainStoredPath(path)
                ?: continue
            if (query.match(retained)) {
                result[retained] = entry
            }
        }
        return result
    }


    // A reserved marker segment routes per-flavour; otherwise the leading segment IS the element stable id.
    // Ported verbatim from the retired ServerLogicController.tracePathOf.
    private fun tracePathOf(address: Address, stableId: ObjectStableId): LogicTracePath {
        val segment = address.segments.first()
        val routing = routingByMarker[segment]
        return routing?.tracePath(address, stableId)
            ?: LogicTracePath.ofObjectStableId(ObjectStableId(segment))
    }


    // Ported verbatim from the retired LogicTraceStore: keep a stable-id path through a rename (the client
    // resolves it), drop it once its object is deleted. A node-outcome path is stable-id-keyed too (under its
    // own marker), so it drops on delete alongside the emit path. A non-stable (literal Report) path passes
    // through.
    private fun retainStoredPath(storedPath: LogicTracePath): LogicTracePath? {
        val stableId = storedPath.objectStableId()
            ?: storedPath.outcomeStableId()
            ?: return storedPath
        return try {
            objectStableMapper.objectLocation(stableId)
            storedPath
        }
        catch (_: IllegalArgumentException) {
            null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun mostRecentNode(root: Node, stableId: ObjectStableId): Node? {
        var best: Node? = null
        forEachNode(root) { node ->
            if (node.stableId == stableId &&
                (best == null || nodeOrdinal(node.id) > nodeOrdinal(best!!.id))
            ) {
                best = node
            }
        }
        return best
    }


    private fun findNode(node: Node, target: NodeId): Node? {
        if (node.id == target) {
            return node
        }
        for (child in node.children) {
            findNode(child, target)?.let { return it }
        }
        return null
    }


    private fun forEachNode(node: Node, action: (Node) -> Unit) {
        action(node)
        node.children.forEach { forEachNode(it, action) }
    }


    // The engine assigns node ids "n0", "n1", ... in creation order, so the ordinal is a monotonic proxy for
    // "most recent invocation" (a later host() gets a higher counter).
    private fun nodeOrdinal(id: NodeId): Long {
        return id.value.removePrefix("n").toLongOrNull() ?: -1L
    }
}
