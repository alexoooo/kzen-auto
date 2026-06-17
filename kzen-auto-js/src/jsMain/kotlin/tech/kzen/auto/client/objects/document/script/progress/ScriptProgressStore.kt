package tech.kzen.auto.client.objects.document.script.progress

import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.script.model.RunStepInstructions
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation


class ScriptProgressStore(
    private val scriptStore: ScriptStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Backstop against pathological instruction graphs; the visited-set already prevents cycles,
        // so this only bounds the per-refresh REST fan-out depth.
        private const val maxSubScriptDepth = 8
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun refresh() {
        val logicRunExecutionId = mostRecent()
        if (logicRunExecutionId == null) {
            scriptStore.update { state -> state
                .withProgressSuccess {
                    it.copy(
                        logicTraceSnapshot = null,
                        loaded = true
                    )
                }
            }
            return
        }

        val logicTraceQuery = LogicTraceQuery(LogicTracePath.root)

        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val progressResult = progressQuery(
            logicRunExecutionId.logicRunId, logicRunExecutionId.logicExecutionId, logicTraceQuery)

        when (progressResult) {
            is ClientError -> {
                scriptStore.update { state -> state
                    .withGlobalError(progressResult.message)
                    .withProgressSuccess {
                        it.copy(
                            logicTraceSnapshot = null,
                            loaded = true
                        )
                    }
                }
            }

            is ClientSuccess -> {
                // Fold in the screenshots from every reachable sub-script (RunStep -> instructions),
                // keyed by their own ObjectStableIds. Main entries are applied last so the parent wins
                // any key collision (notably the fixed `next-step` pointer).
                val subScriptValues = collectSubScriptValues()
                val mergedSnapshot = LogicTraceSnapshot(subScriptValues + progressResult.value.values)

                scriptStore.update { state -> state
                    .withProgressSuccess {
                        it.copy(
                            logicTraceSnapshot = mergedSnapshot,
                            loaded = true
                        )
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Walk the current document's RunSteps to their instructions sub-scripts (recursively), and for
    // each already-run sub-script pull its trace snapshot. Every step is independently guarded — a
    // sub-script that never ran (null mostRecent), an evicted buffer, or an unresolvable link simply
    // contributes nothing and never aborts the main snapshot.
    private suspend fun collectSubScriptValues(): Map<LogicTracePath, ExecutionValue> {
        val clientState = scriptStore.clientStateGlobal.current()
            ?: return mapOf()
        val graphNotation = clientState.graphStructure().graphNotation
        val graphDefinition = clientState.graphDefinitionAttempt.successful()

        val out = mutableMapOf<LogicTracePath, ExecutionValue>()
        val visited = mutableSetOf<DocumentPath>()
        visited.add(scriptStore.mainLocation().documentPath)

        walkSubScripts(
            scriptStore.mainLocation().documentPath, 0, visited, out, graphNotation, graphDefinition)

        return out
    }


    private suspend fun walkSubScripts(
        documentPath: DocumentPath,
        depth: Int,
        visited: MutableSet<DocumentPath>,
        out: MutableMap<LogicTracePath, ExecutionValue>,
        graphNotation: GraphNotation,
        graphDefinition: GraphDefinition
    ) {
        if (depth >= maxSubScriptDepth) {
            return
        }

        for (runStepLocation in RunStepInstructions.runStepLocations(graphNotation, documentPath)) {
            val instructionsLocation = RunStepInstructions.instructionsLocation(graphNotation, runStepLocation)
                ?: continue

            val instructionsDocumentPath = instructionsLocation.documentPath
            if (instructionsDocumentPath in visited) {
                // Cycle guard / shared sub-script dedup — its traces were (or are being) folded in already.
                continue
            }
            visited.add(instructionsDocumentPath)

            val recent = mostRecentQuery(instructionsDocumentPath, instructionsLocation.objectPath)
            val runExecutionId = (recent as? ClientSuccess)?.value?.logicRunExecutionId
            if (runExecutionId != null) {
                val progressResult = progressQuery(
                    runExecutionId.logicRunId,
                    runExecutionId.logicExecutionId,
                    LogicTraceQuery(LogicTracePath.root))
                if (progressResult is ClientSuccess) {
                    out.putAll(progressResult.value.values)
                }
            }

            walkSubScripts(
                instructionsDocumentPath, depth + 1, visited, out, graphNotation, graphDefinition)
        }
    }


    private suspend fun progressQuery(
        logicRunId: LogicRunId,
        logicExecutionId: LogicExecutionId,
        logicTraceQuery: LogicTraceQuery
    ): ClientResult<LogicTraceSnapshot> {
        val result = scriptStore.restClient.performDetached(
            LogicConventions.logicTraceEndpointLocation,
            CommonRestApi.paramAction to LogicConventions.actionLookup,
            CommonRestApi.paramRunId to logicRunId.value,
            CommonRestApi.paramExecutionId to logicExecutionId.value,
            LogicConventions.paramQuery to logicTraceQuery.asString()
        )

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, Map<String, Any>>

                val inputBrowserInfo = LogicTraceSnapshot.ofCollection(resultValue)
                ClientResult.ofSuccess(inputBrowserInfo)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun mostRecent(): LogicRunExecutionId? {
        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val mostRecentResult = mostRecentQuery()

        return when (mostRecentResult) {
            is ClientError -> {
                scriptStore.update { state -> state
                    .withGlobalError(mostRecentResult.message)
                }
                null
            }

            is ClientSuccess -> {
                scriptStore.update { state -> state
                    .withProgressSuccess {
                        it.copy(
                            logicRunExecutionId = mostRecentResult.value.logicRunExecutionId
                        )
                    }
                }

                mostRecentResult.value.logicRunExecutionId
            }
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
    suspend fun clear() {
        val logicRunExecutionId = mostRecent()
        if (logicRunExecutionId == null) {
            scriptStore.update { state -> state
                .withProgressSuccess {
                    it.copy(
                        logicRunExecutionId = null,
                        logicTraceSnapshot = null)
                }
            }
            return
        }

        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val clearResult = clearCommand()

        when (clearResult) {
            is ClientError -> {
                scriptStore.update { state -> state
                    .withGlobalError(clearResult.message)
                }
            }

            is ClientSuccess -> {
                scriptStore.update { state -> state
                    .withProgressSuccess {
                        it.copy(
                            logicRunExecutionId = null,
                            logicTraceSnapshot = null)
                    }
                }
            }
        }
    }


    private suspend fun clearCommand(): ClientResult<Boolean> {
        val mainLocation = scriptStore.mainLocation()

        val result = scriptStore.restClient.performDetached(
            LogicConventions.logicTraceEndpointLocation,
            CommonRestApi.paramAction to LogicConventions.actionReset,
            LogicConventions.paramSubDocumentPath to mainLocation.documentPath.asString(),
            LogicConventions.paramSubObjectPath to mainLocation.objectPath.asString()
        )

        return when (result) {
            is ExecutionSuccess -> {
                val resultValue = result.value.get() as Boolean
                ClientResult.ofSuccess(resultValue)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }
}