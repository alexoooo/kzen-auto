package tech.kzen.auto.server.exec.job

import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


/**
 * A Job as a [Logic]: a graph of concurrently-running Workers connected by Channels (the third kzen-auto
 * paradigm, beside Script and Flow), run on the new engine. Thin and immutable — the structure is compiled once
 * by [JobLogicCompiler]; each [run] call makes a fresh [JobRun] holding that call's per-run instance graph, so
 * one [JobLogic] can be hosted more than once (e.g. a Job nested in a Script). The signature is empty in this
 * first port (no declared parameters / harvested output channels yet — matching
 * [tech.kzen.auto.server.objects.job.JobDocument]'s `define`).
 */
class JobLogic(
    private val filteredDefinition: GraphDefinition,
    private val workerLocations: List<ObjectLocation>,
    private val channelLocations: List<ObjectLocation>,
    private val logicSignature: LogicSignature,
    private val objectStableMapper: ObjectStableMapper,
    private val graphEnvironment: GraphEnvironment
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
            objectStableMapper,
            graphEnvironment
        ).run()
    }
}
