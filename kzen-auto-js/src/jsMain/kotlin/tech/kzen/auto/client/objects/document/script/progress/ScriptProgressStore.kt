package tech.kzen.auto.client.objects.document.script.progress

import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.client.service.logic.LogicRunFrames
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.BinaryExecutionValue
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
    // only fetches events newer than the watermark. Reset when the run id changes or on clear().
    private var historyRunId: LogicRunId? = null
    private val historyEvents = mutableListOf<LogicTraceEvent>()


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
            resetHistory()
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
                    historyRunId = logicRunId
                    historyEvents.clear()
                }
                val sinceSequence = historyEvents.maxOfOrNull { it.sequence } ?: 0L
                val historyResult = lookupRunHistoryQuery(logicRunId, sinceSequence)
                if (historyResult is ClientSuccess) {
                    historyEvents.addAll(historyResult.value)
                }
                val events = historyEvents.sortedBy { it.sequence }

                // The execution tree (parent + call-site per execution) scopes each RunStep's view to the
                // executions IT spawned within the viewed frame — re-fetched in full each refresh (it is
                // tiny, one row per execution, unlike the watermarked event history). Refresh cadence is
                // bounded upstream: ClientLogicGlobal throttles status publishes, so this runs ~1/s during a
                // run rather than per engine emit.
                val executions = when (val executionsResult = lookupRunExecutionsQuery(logicRunId)) {
                    is ClientSuccess -> executionsResult.value
                    is ClientError -> listOf()
                }
                val runStepOwnedExecutions = computeRunStepOwnedExecutions(
                    logicRunExecutionId.logicExecutionId, executions)
                val runStepRepresentative = computeRunStepRepresentative(runStepOwnedExecutions, events)

                scriptStore.update { state -> state
                    .withProgressSuccess {
                        it.copy(
                            logicRunExecutionId = logicRunExecutionId,
                            logicTraceSnapshot = snapshot,
                            nextToRun = activeFrame?.position,
                            traceEvents = events,
                            runStepRepresentative = runStepRepresentative,
                            runStepOwnedExecutions = runStepOwnedExecutions,
                            loaded = true
                        )
                    }
                }
            }
        }
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
        if (! out.add(node.executionId.value)) {
            // Already collected — also guards against a pathological cycle in the tree.
            return
        }
        for (child in childrenByParent[node.executionId.value].orEmpty()) {
            collectExecutionSubtree(child, childrenByParent, out)
        }
    }


    // For each RunStep, the latest screenshot-bearing event among the executions it owns — the
    // right-of-step thumbnail (and full-screen viewer entry), surviving a rename mid-run.
    private fun computeRunStepRepresentative(
        runStepOwnedExecutions: Map<ObjectStableId, Set<String>>,
        events: List<LogicTraceEvent>
    ): Map<ObjectStableId, LogicTraceEvent> {
        val binaryEvents = events.filter { it.value is BinaryExecutionValue }
        if (binaryEvents.isEmpty() || runStepOwnedExecutions.isEmpty()) {
            return mapOf()
        }

        val out = mutableMapOf<ObjectStableId, LogicTraceEvent>()
        for ((runStepStableId, owned) in runStepOwnedExecutions) {
            val representative = binaryEvents
                .filter { it.executionId.value in owned }
                .maxByOrNull { it.sequence }
            if (representative != null) {
                out[runStepStableId] = representative
            }
        }
        return out
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
    private fun resetHistory() {
        historyRunId = null
        historyEvents.clear()
    }
}
