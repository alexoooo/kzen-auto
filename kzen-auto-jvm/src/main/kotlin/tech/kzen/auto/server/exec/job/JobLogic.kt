package tech.kzen.auto.server.exec.job

import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation


/**
 * A Job as a [Logic]: a graph of concurrently-running Workers connected by Channels (the third kzen-auto
 * paradigm, beside Script and Flow), run on the new engine. Thin and immutable — the structure is compiled once
 * by [JobLogicCompiler]; each [run] call makes a fresh [JobRun] holding that call's per-run instance graph, so
 * one [JobLogic] can be hosted more than once (e.g. a Job nested in a Script). The signature is derived by
 * [JobLogicCompiler] from the document's `parameters` declarations and ResultSink Workers (see
 * [tech.kzen.auto.common.objects.document.job.JobSignatureCapability]), so a Job can be hosted with arguments and
 * its result consumed like any other Logic.
 *
 * The un-filtered [graphNotation] / [graphDefinition] and [services] are carried so a nested-Logic
 * [RunWorker][tech.kzen.auto.server.objects.job.worker.RunWorker] can compile its child from the full graph
 * (its child is a different document, outside this Job's [filteredDefinition]).
 */
class JobLogic(
    private val filteredDefinition: GraphDefinition,
    private val workerLocations: List<ObjectLocation>,
    private val channelLocations: List<ObjectLocation>,
    private val logicSignature: LogicSignature,
    private val jobParameters: JobParameters,
    private val graphNotation: GraphNotation,
    private val graphDefinition: GraphDefinition,
    private val services: LogicCompilerServices
): Logic {
    override fun signature(): LogicSignature {
        return logicSignature
    }


    override suspend fun run(execution: Execution): TupleValue {
        return JobRun(
            execution,
            filteredDefinition,
            workerLocations,
            channelLocations,
            jobParameters,
            graphNotation,
            graphDefinition,
            services
        ).run()
    }
}
