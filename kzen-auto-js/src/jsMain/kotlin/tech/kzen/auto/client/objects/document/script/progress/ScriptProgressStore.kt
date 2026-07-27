package tech.kzen.auto.client.objects.document.script.progress

import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.client.service.logic.LogicRunFrames
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.BinaryValue
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionInfo
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceEvent
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.store.normal.ObjectStableId


class ScriptProgressStore(
    private val scriptStore: ScriptStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    // Accumulated history for the current run, held here (not read back from state) so each refresh
    // only fetches events newer than the watermark. Append-only and ascending by sequence (see
    // appendHistory), so the watermark is the last element. Reset when the run id changes or on clear().
    private var historyRunId: LogicRunId? = null
    private val historyEvents = mutableListOf<LogicTraceEvent>()

    // Immutable snapshot of historyEvents for publishing — rebuilt only when the accumulation actually
    // changed, so the published reference stays stable across no-news refreshes (letting ScriptStore's
    // updateIfChanged bail) and the mutable accumulator is never exposed to state.
    private var publishedEvents: List<LogicTraceEvent> = listOf()

    // The execution tree, re-fetched only when the run's STRUCTURE changes (an execution created/destroyed),
    // not per publish: its answer is structural, so gating it on structureVersion drops it from ~46 to ~15-17
    // fetches/run. The cached set is reused between structural changes (runStep ownership/representatives
    // recompute cheaply from it plus the fresh events); reset on a new run alongside the history.
    private var lastExecutionsStructureVersion: String? = null
    private var lastExecutions: List<LogicRunExecutionInfo> = listOf()

    // RunStep ownership memo: recomputed only when the viewed execution or the (structureVersion-cached)
    // executions list identity changes, and kept value-stable so a same-content refetch preserves the
    // published references.
    private var ownershipViewedExecutionId: LogicExecutionId? = null
    private var ownershipExecutions: List<LogicRunExecutionInfo>? = null
    private var ownershipByStep: Map<ObjectStableId, Set<String>> = mapOf()

    // Reverse index of ownershipByStep (the owned sets are disjoint — the execution tree is a tree, and
    // same-caller subtrees merge into one set), so a new event resolves its owning RunStep in O(1).
    private var stepByExecutionId: Map<String, ObjectStableId> = mapOf()

    // Latest screenshot-bearing event per RunStep, folded forward from each refresh's appended tail rather
    // than rescanned over the whole accumulated timeline.
    private var representativeByStep: Map<ObjectStableId, LogicTraceEvent> = mapOf()


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun refresh() {
        // A document LIVE in the run is shown by its OWN frame's execution id (frame-keyed), so sequential
        // re-entries and parallel invocations of the same document don't bleed together through the run-merge;
        // otherwise the most-recent invocation merged across the run (post-run inspection of the last one).
        val activeRun = scriptStore.clientStateGlobal.current()
            ?.clientLogicState?.logicStatus?.active
        val activeFrame = LogicRunFrames.frameForDocument(
            activeRun?.frame, scriptStore.mainLocation().documentPath)

        val logicRunExecutionId =
            if (activeRun != null && activeFrame != null) {
                LogicRunExecutionId(activeRun.id, activeFrame.executionId)
            }
            else {
                mostRecent()
            }

        if (logicRunExecutionId == null) {
            resetRunAccumulators(null)
            scriptStore.update { state -> state
                .withProgressSuccess {
                    it.copy(
                        logicRunExecutionId = null,
                        logicTraceSnapshot = null,
                        nextToRun = null,
                        traceEvents = listOf(),
                        runStepRepresentative = mapOf(),
                        runStepOwnedExecutions = mapOf(),
                        loaded = true
                    )
                }
            }
            return
        }

        val logicRunId = logicRunExecutionId.logicRunId

        // The per-path snapshot drives live step state and non-RunStep thumbnails: the live frame's
        // own buffer (single execution) when this document is executing, else merged across the run. If the
        // single-execution lookup misses, the live frame simply hasn't emitted yet (its buffer opens on the
        // first mirrored event — after the park at its first step boundary), so its trace IS empty: render
        // that, NOT a run-merged fallback, which would ghost the PREVIOUS same-document invocation's retained
        // values onto a freshly-entered sub-script until its first step's emit clears them.
        // (The next-step highlight reads the frame's position, not this snapshot.)
        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val progressResult =
            if (activeFrame != null) {
                lookupQuery(logicRunExecutionId, LogicTraceQuery(LogicTracePath.root))
                    .let { frameResult ->
                        if (frameResult is ClientError) {
                            ClientResult.ofSuccess(LogicTraceSnapshot(mapOf()))
                        }
                        else {
                            frameResult
                        }
                    }
            }
            else {
                lookupRunQuery(logicRunId, LogicTraceQuery(LogicTracePath.root))
            }

        when (progressResult) {
            is ClientError -> {
                scriptStore.update { state -> state
                    .withGlobalError(progressResult.message)
                    .withProgressSuccess {
                        it.copy(
                            logicRunExecutionId = logicRunExecutionId,
                            logicTraceSnapshot = null,
                            nextToRun = null,
                            traceEvents = listOf(),
                            runStepRepresentative = mapOf(),
                            runStepOwnedExecutions = mapOf(),
                            loaded = true
                        )
                    }
                }
            }

            is ClientSuccess -> {
                val snapshot = progressResult.value

                // Incremental history: a new run resets the accumulation; otherwise pull only events
                // past the current watermark and append. The retained timeline survives loop clears.
                if (historyRunId != logicRunId) {
                    resetRunAccumulators(logicRunId)
                }
                val sinceSequence = historyEvents.lastOrNull()?.sequence ?: 0L
                val historyResult = lookupRunHistoryQuery(logicRunId, sinceSequence)
                val historyAppend =
                    if (historyResult is ClientSuccess) {
                        appendHistory(historyResult.value)
                    }
                    else {
                        HistoryAppend(listOf(), false)
                    }

                // The execution tree (parent + call-site per execution) scopes each RunStep's view to the
                // executions IT spawned within the viewed frame. Its answer is structural, so re-fetch only on
                // a structureVersion change (an execution created/destroyed) and reuse the cache otherwise —
                // dropping it from once-per-publish to ~15-17/run. runStep ownership/representatives below
                // recompute cheaply from the cached set plus the fresh events.
                val structureVersion = scriptStore.clientStateGlobal.current()
                    ?.clientLogicState?.structureVersion()
                if (structureVersion == null || structureVersion != lastExecutionsStructureVersion) {
                    when (val executionsResult = lookupRunExecutionsQuery(logicRunId)) {
                        is ClientSuccess -> {
                            lastExecutions = executionsResult.value
                            lastExecutionsStructureVersion = structureVersion
                        }
                        is ClientError -> {
                            // Don't cache a failure as the answer; leave the version unset so the next refresh
                            // retries rather than reusing an empty set until the structure changes again.
                            lastExecutions = listOf()
                            lastExecutionsStructureVersion = null
                        }
                    }
                }
                // Ownership first, then representatives: a changed ownership map (or a repaired timeline,
                // whose appended tail is no longer the tail) needs a full pass over the accumulated events,
                // otherwise only this refresh's new events are folded in.
                val ownershipChanged = refreshOwnership(
                    logicRunExecutionId.logicExecutionId, lastExecutions)
                if (ownershipChanged || historyAppend.repaired) {
                    rebuildRepresentatives()
                }
                else {
                    foldRepresentatives(historyAppend.newEvents)
                }

                scriptStore.update { state -> state
                    .withProgressSuccess {
                        it.copy(
                            logicRunExecutionId = logicRunExecutionId,
                            logicTraceSnapshot = snapshot,
                            nextToRun = activeFrame?.position,
                            traceEvents = publishedEvents,
                            runStepRepresentative = representativeByStep,
                            runStepOwnedExecutions = ownershipByStep,
                            loaded = true
                        )
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private class HistoryAppend(
        val newEvents: List<LogicTraceEvent>,
        val repaired: Boolean
    )


    // Append only genuinely-new events, preserving the ascending-by-sequence invariant. Events at or below
    // the watermark are dropped: the server serves strictly > sinceSequence (RunEngineLogicTrace, over an
    // append-only single-writer list), so a re-delivery can only come from a concurrent in-flight refresh
    // that read an older watermark — dropping keeps the film strip duplicate-free. A within-batch order
    // violation would break the server's contract: warn and repair once (rather than throw inside the
    // refresh coroutine), signalling the caller to rebuild derived state.
    private fun appendHistory(batch: List<LogicTraceEvent>): HistoryAppend {
        if (batch.isEmpty()) {
            return HistoryAppend(listOf(), false)
        }

        var watermark = historyEvents.lastOrNull()?.sequence ?: 0L
        var previousInBatch = Long.MIN_VALUE
        var orderViolation = false
        val appended = mutableListOf<LogicTraceEvent>()

        for (event in batch) {
            if (event.sequence <= previousInBatch) {
                orderViolation = true
            }
            previousInBatch = event.sequence

            if (event.sequence <= watermark) {
                continue
            }

            historyEvents.add(event)
            appended.add(event)
            watermark = event.sequence
        }

        if (orderViolation) {
            console.warn("ScriptProgressStore: non-monotonic history batch, repairing")
            historyEvents.sortBy { it.sequence }
        }

        if (appended.isNotEmpty() || orderViolation) {
            publishedEvents = historyEvents.toList()
        }

        return HistoryAppend(appended, orderViolation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Recompute the RunStep ownership (and its reverse index) only when its inputs actually changed,
    // returning true when the derived map's CONTENT changed — i.e. when the representatives need a full
    // rebuild against the new scoping. A same-content refetch keeps the existing references.
    private fun refreshOwnership(
        viewedExecutionId: LogicExecutionId,
        executions: List<LogicRunExecutionInfo>
    ): Boolean {
        if (viewedExecutionId == ownershipViewedExecutionId && executions === ownershipExecutions) {
            return false
        }
        ownershipViewedExecutionId = viewedExecutionId
        ownershipExecutions = executions

        val computed = computeRunStepOwnedExecutions(viewedExecutionId, executions)
        if (computed == ownershipByStep) {
            return false
        }
        ownershipByStep = computed

        stepByExecutionId = buildMap {
            for ((runStepStableId, owned) in computed) {
                for (executionId in owned) {
                    put(executionId, runStepStableId)
                }
            }
        }
        return true
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The execution tree, scoped to the viewed document's resolved execution (eD). Each direct child of eD
    // is one RunStep invocation in the viewed document; that child and its whole transitive subtree of
    // executions are owned by the RunStep named by the child's call-site. Keyed by RunStep ObjectStableId
    // (rename-safe) -> the set of owned execution-id values. A RunStep that didn't run under eD has no
    // entry (so its strip is empty — fixing the "shows before it runs" bleed-through).
    private fun computeRunStepOwnedExecutions(
        viewedExecutionId: LogicExecutionId,
        executions: List<LogicRunExecutionInfo>
    ): Map<ObjectStableId, Set<String>> {
        if (executions.isEmpty()) {
            return mapOf()
        }

        val childrenByParent = mutableMapOf<String, MutableList<LogicRunExecutionInfo>>()
        for (execution in executions) {
            val parent = execution.parentExecutionId?.value
                ?: continue
            childrenByParent.getOrPut(parent) { mutableListOf() }.add(execution)
        }

        val out = mutableMapOf<ObjectStableId, MutableSet<String>>()
        for (directChild in childrenByParent[viewedExecutionId.value].orEmpty()) {
            val caller = directChild.callerStableId
                ?: continue
            val owned = out.getOrPut(caller) { mutableSetOf() }
            collectExecutionSubtree(directChild, childrenByParent, owned)
        }
        return out
    }


    private fun collectExecutionSubtree(
        node: LogicRunExecutionInfo,
        childrenByParent: Map<String, List<LogicRunExecutionInfo>>,
        out: MutableSet<String>
    ) {
        if (!out.add(node.executionId.value)) {
            // Already collected — also guards against a pathological cycle in the tree.
            return
        }
        for (child in childrenByParent[node.executionId.value].orEmpty()) {
            collectExecutionSubtree(child, childrenByParent, out)
        }
    }


    // For each RunStep, the latest screenshot-bearing event among the executions it owns — the
    // right-of-step thumbnail (and full-screen viewer entry), surviving a rename mid-run.
    // Full pass over the accumulated timeline; runs only when the ownership scoping changed (or the
    // timeline was repaired). Ascending order means last write wins, i.e. the same selection the former
    // per-RunStep maxByOrNull made.
    private fun rebuildRepresentatives() {
        val out = mutableMapOf<ObjectStableId, LogicTraceEvent>()
        for (event in historyEvents) {
            if (event.value !is BinaryValue) {
                continue
            }
            val runStepStableId = stepByExecutionId[event.executionId.value]
                ?: continue
            out[runStepStableId] = event
        }
        representativeByStep = out
    }


    // Per-refresh path: fold in only this refresh's appended tail, copy-on-write so the published map
    // keeps its reference when no screenshot landed.
    private fun foldRepresentatives(newEvents: List<LogicTraceEvent>) {
        var updates: MutableMap<ObjectStableId, LogicTraceEvent>? = null

        for (event in newEvents) {
            if (event.value !is BinaryValue) {
                continue
            }
            val runStepStableId = stepByExecutionId[event.executionId.value]
                ?: continue

            val target = updates
                ?: mutableMapOf<ObjectStableId, LogicTraceEvent>().also { updates = it }
            target[runStepStableId] = event
        }

        val changed = updates
            ?: return
        representativeByStep = representativeByStep + changed
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Single-execution snapshot for one frame's invocation (frame-keyed view), keyed by run + execution id —
    // does NOT merge sibling invocations the way lookupRun does.
    private suspend fun lookupQuery(
        logicRunExecutionId: LogicRunExecutionId,
        logicTraceQuery: LogicTraceQuery
    ): ClientResult<LogicTraceSnapshot> {
        val result = scriptStore.restClient.performDetached(
            LogicConventions.logicTraceEndpointLocation,
            CommonRestApi.paramAction to LogicConventions.actionLookup,
            CommonRestApi.paramRunId to logicRunExecutionId.logicRunId.value,
            CommonRestApi.paramExecutionId to logicRunExecutionId.logicExecutionId.value,
            LogicConventions.paramQuery to logicTraceQuery.asString()
        )

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, Map<String, Any>>
                ClientResult.ofSuccess(LogicTraceSnapshot.ofCollection(resultValue))
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }


    private suspend fun lookupRunQuery(
        logicRunId: LogicRunId,
        logicTraceQuery: LogicTraceQuery
    ): ClientResult<LogicTraceSnapshot> {
        val result = scriptStore.restClient.performDetached(
            LogicConventions.logicTraceEndpointLocation,
            CommonRestApi.paramAction to LogicConventions.actionLookupRun,
            CommonRestApi.paramRunId to logicRunId.value,
            LogicConventions.paramQuery to logicTraceQuery.asString()
        )

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, Map<String, Any>>
                ClientResult.ofSuccess(LogicTraceSnapshot.ofCollection(resultValue))
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }


    private suspend fun lookupRunHistoryQuery(
        logicRunId: LogicRunId,
        sinceSequence: Long
    ): ClientResult<List<LogicTraceEvent>> {
        val result = scriptStore.restClient.performDetached(
            LogicConventions.logicTraceEndpointLocation,
            CommonRestApi.paramAction to LogicConventions.actionLookupRunHistory,
            CommonRestApi.paramRunId to logicRunId.value,
            LogicConventions.paramSinceSequence to sinceSequence.toString()
        )

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as List<Map<String, Any>>
                ClientResult.ofSuccess(resultValue.map { LogicTraceEvent.ofCollection(it) })
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }


    private suspend fun lookupRunExecutionsQuery(
        logicRunId: LogicRunId
    ): ClientResult<List<LogicRunExecutionInfo>> {
        val result = scriptStore.restClient.performDetached(
            LogicConventions.logicTraceEndpointLocation,
            CommonRestApi.paramAction to LogicConventions.actionLookupRunExecutions,
            CommonRestApi.paramRunId to logicRunId.value
        )

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as List<Map<String, Any>>
                ClientResult.ofSuccess(resultValue.map { LogicRunExecutionInfo.ofCollection(it) })
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun mostRecent(): LogicRunExecutionId? {
        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val mostRecentResult = mostRecentQuery()

        // The resolved id is persisted by refresh() (uniformly for both the frame-keyed and this fallback
        // path), so this only reports a query failure.
        return when (mostRecentResult) {
            is ClientError -> {
                scriptStore.update { state -> state
                    .withGlobalError(mostRecentResult.message)
                }
                null
            }

            is ClientSuccess ->
                mostRecentResult.value.logicRunExecutionId
        }
    }


    private suspend fun mostRecentQuery(): ClientResult<ScriptProgressState.MostRecentResult> {
        val mainLocation = scriptStore.mainLocation()
        return mostRecentQuery(mainLocation.documentPath, mainLocation.objectPath)
    }


    private suspend fun mostRecentQuery(
        documentPath: DocumentPath,
        objectPath: ObjectPath
    ): ClientResult<ScriptProgressState.MostRecentResult> {
        val result = scriptStore.restClient.performDetached(
            LogicConventions.logicTraceEndpointLocation,
            CommonRestApi.paramAction to LogicConventions.actionMostRecent,
            LogicConventions.paramSubDocumentPath to documentPath.asString(),
            LogicConventions.paramSubObjectPath to objectPath.asString()
        )

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultCollection = result.value.get() as Map<String, String>?

                val resultValue = resultCollection?.let { LogicConventions.runExecutionFromCollection(it) }
                ClientResult.ofSuccess(ScriptProgressState.MostRecentResult(resultValue))
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Drop everything accumulated for the previous run: the timeline watermark, the cached execution tree,
    // and the ownership/representative memos derived from both. Called with the new run's id when the run
    // changes, and with null when there is no run at all (a JVM restart drops runs entirely).
    // NB: deliberately NOT called on a live-edit migration — the engine preserves history, sequence and
    // runId across it, so the trace (and hence the watermark) is continuous.
    private fun resetRunAccumulators(newRunId: LogicRunId?) {
        historyRunId = newRunId
        historyEvents.clear()
        publishedEvents = listOf()

        lastExecutions = listOf()
        lastExecutionsStructureVersion = null

        ownershipViewedExecutionId = null
        ownershipExecutions = null
        ownershipByStep = mapOf()
        stepByExecutionId = mapOf()
        representativeByStep = mapOf()
    }
}
