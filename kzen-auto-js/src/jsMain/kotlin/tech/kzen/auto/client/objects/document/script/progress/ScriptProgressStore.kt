package tech.kzen.auto.client.objects.document.script.progress

import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.client.service.logic.LogicRunFrames
import tech.kzen.auto.common.objects.document.script.model.RunStepInstructions
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceEvent
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.store.normal.ObjectStableId


class ScriptProgressStore(
    private val scriptStore: ScriptStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Backstop against pathological instruction graphs; the visited-set already prevents cycles,
        // so this only bounds the per-refresh sub-script recursion depth.
        private const val maxSubScriptDepth = 8
    }


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
                        traceEvents = listOf(),
                        runStepRepresentative = mapOf(),
                        loaded = true
                    )
                }
            }
            return
        }

        val logicRunId = logicRunExecutionId.logicRunId

        // The per-path snapshot drives live step state / next-step and non-RunStep thumbnails: the live frame's
        // own buffer (single execution) when this document is executing, else merged across the run. If the
        // single-execution lookup misses (a just-evicted / racing frame), fall back to the merged run so the
        // view still renders.
        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val progressResult =
            if (activeFrame != null) {
                lookupQuery(logicRunExecutionId, LogicTraceQuery(LogicTracePath.root))
                    .let { frameResult ->
                        if (frameResult is ClientError) {
                            lookupRunQuery(logicRunId, LogicTraceQuery(LogicTracePath.root))
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
                            traceEvents = listOf(),
                            runStepRepresentative = mapOf(),
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

                val runStepRepresentative = computeRunStepRepresentative(events)

                scriptStore.update { state -> state
                    .withProgressSuccess {
                        it.copy(
                            logicRunExecutionId = logicRunExecutionId,
                            logicTraceSnapshot = snapshot,
                            traceEvents = events,
                            runStepRepresentative = runStepRepresentative,
                            loaded = true
                        )
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // For every reachable RunStep, the latest screenshot-bearing event anywhere in its subtree
    // (instructions sub-script + nested RunSteps). Keyed stable-id -> event so the right-of-step
    // thumbnail can show it and survive a rename mid-run (see ScriptProgressState).
    private fun computeRunStepRepresentative(
        events: List<LogicTraceEvent>
    ): Map<ObjectStableId, LogicTraceEvent> {
        val clientState = scriptStore.clientStateGlobal.current()
            ?: return mapOf()
        val graphNotation = clientState.graphStructure().graphNotation

        val binaryEvents = events.filter { it.value is BinaryExecutionValue }
        if (binaryEvents.isEmpty()) {
            return mapOf()
        }

        val out = mutableMapOf<ObjectStableId, LogicTraceEvent>()
        val visited = mutableSetOf<DocumentPath>()
        visited.add(scriptStore.mainLocation().documentPath)

        walkRunStepRepresentative(
            scriptStore.mainLocation().documentPath, 0, visited, out, graphNotation, binaryEvents)

        return out
    }


    private fun walkRunStepRepresentative(
        documentPath: DocumentPath,
        depth: Int,
        visited: MutableSet<DocumentPath>,
        out: MutableMap<ObjectStableId, LogicTraceEvent>,
        graphNotation: GraphNotation,
        binaryEvents: List<LogicTraceEvent>
    ) {
        if (depth >= maxSubScriptDepth) {
            return
        }

        for (runStepLocation in RunStepInstructions.runStepLocations(graphNotation, documentPath)) {
            val instructionsLocation = RunStepInstructions.instructionsLocation(graphNotation, runStepLocation)
                ?: continue

            val subtreeRoots = RunStepInstructions
                .subtreeInstructionRoots(graphNotation, runStepLocation)
                .mapTo(mutableSetOf()) { scriptStore.objectStableMapper.objectStableId(it) }

            val representative = binaryEvents
                .filter { it.rootStableId in subtreeRoots }
                .maxByOrNull { it.sequence }
            if (representative != null) {
                out[scriptStore.objectStableMapper.objectStableId(runStepLocation)] = representative
            }

            // Recurse for nested RunSteps (so they get their own entry); the visited-set dedups shared
            // sub-scripts and guards cycles.
            val instructionsDocumentPath = instructionsLocation.documentPath
            if (instructionsDocumentPath !in visited) {
                visited.add(instructionsDocumentPath)
                walkRunStepRepresentative(
                    instructionsDocumentPath, depth + 1, visited, out, graphNotation, binaryEvents)
            }
        }
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
