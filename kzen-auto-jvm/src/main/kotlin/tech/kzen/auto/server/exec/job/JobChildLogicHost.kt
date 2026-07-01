package tech.kzen.auto.server.exec.job

import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation


/**
 * Compiles (once, then caches) the child Logics a Job's Workers host — the shared backing for
 * [JobControl.host][tech.kzen.auto.common.paradigm.job.control.JobControl.host] / [RunWorker]. A child is any
 * runnable Logic document (a Script / Flow / Job), compiled via the flavour-agnostic
 * [LogicCompiler] exactly as a Script's [RunStep][tech.kzen.auto.server.objects.script.step.control.RunStep]
 * and a Flow's [RunLogicVertex][tech.kzen.auto.server.objects.flow.vertex.RunLogicVertex] compile theirs — so
 * a Worker can host any paradigm with no per-type knowledge here.
 *
 * ONE instance is shared across all of a run's Workers (built per [JobRun]): compilation is pure and keyed by
 * the child's location, so a Worker in a loop reuses its compiled child and two Workers pointing at the same
 * child compile it once. Because the Workers run concurrently, [compile] is synchronized. The actual hosting
 * ([tech.kzen.lib.common.exec.engine.Execution.host]) stays per-Worker — each Worker hosts under its own engine
 * node — so this holds only the reusable compiled artefacts, never per-Worker run state.
 */
class JobChildLogicHost(
    private val graphNotation: GraphNotation,
    private val graphDefinition: GraphDefinition,
    private val services: LogicCompilerServices
) {
    private val childLogics = HashMap<ObjectLocation, Logic>()


    @Synchronized
    fun compile(instructions: ObjectLocation): Logic {
        return childLogics.getOrPut(instructions) {
            LogicCompiler.compile(instructions, graphNotation, graphDefinition, services)
        }
    }
}
