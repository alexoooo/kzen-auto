package tech.kzen.auto.server.exec.job

import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.objects.job.worker.content.scope.ScopeMigrationKey
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * A Job as a [Logic]: a graph of concurrently-running Workers connected by Channels (the third kzen-auto
 * paradigm, beside Script and Flow), run on the new engine. Thin and immutable — the structure is compiled once
 * by [JobLogicCompiler]; each [run] call makes a fresh [JobRun] holding that call's per-run instance graph, so
 * one [JobLogic] can be hosted more than once (e.g. a Job nested in a Script). The signature is derived by
 * [JobLogicCompiler] from the document's `parameters` declarations and its declared `results` signature map (see
 * [tech.kzen.auto.common.objects.document.job.JobSignatureCapability]), so a Job can be hosted with arguments and
 * its result consumed like any other Logic.
 *
 * The un-filtered [graphNotation] / [graphDefinition] and [services] are carried so a nested-Logic
 * [RunWorker][tech.kzen.auto.server.objects.job.worker.RunWorker] can compile its child from the full graph
 * (its child is a different document, outside this Job's [filteredDefinition]).
 *
 * [scopeKeys] are the entry scopes' live-edit compatibility keys ([ScopeMigrationKey], spike CS3), by stable id,
 * computed from notation at compile time so [refuseMigration] can judge an edit against the running definition
 * BEFORE the engine's migration barrier detaches anything.
 */
class JobLogic(
    private val jobLocation: ObjectLocation,
    private val filteredDefinition: GraphDefinition,
    private val workerLocations: List<ObjectLocation>,
    private val channelLocations: List<ObjectLocation>,
    private val logicSignature: LogicSignature,
    private val jobParameters: JobParameters,
    private val graphNotation: GraphNotation,
    private val graphDefinition: GraphDefinition,
    private val scopeKeys: Map<ObjectStableId, ScopeMigrationKey>,
    private val services: LogicCompilerServices
): Logic {
    override fun signature(): LogicSignature {
        return logicSignature
    }


    /**
     * The reason [edited] cannot replace this running definition at a live-edit barrier, or null when it can:
     * an entry scope whose source selection changed cannot adopt the open cursor, and the run must go on
     * unedited rather than lose its single-pass source (design §6.7). A scope the edit removed or added is
     * not judged here: the engine closes an unclaimed capture, and a new scope opens its own cursor.
     */
    fun refuseMigration(edited: JobLogic): String? {
        for ((stableId, key) in scopeKeys) {
            val editedKey = edited.scopeKeys[stableId] ?: continue
            key.refusal(editedKey)?.let { return it }
        }
        return null
    }


    override suspend fun run(execution: Execution): DataBindings {
        return JobRun(
            execution,
            jobLocation,
            filteredDefinition,
            workerLocations,
            channelLocations,
            jobParameters,
            logicSignature.outputs,
            graphNotation,
            graphDefinition,
            services
        ).run()
    }
}
