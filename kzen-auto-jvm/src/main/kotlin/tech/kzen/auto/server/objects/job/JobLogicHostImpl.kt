package tech.kzen.auto.server.objects.job

import tech.kzen.auto.common.paradigm.job.api.JobLogicHost
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.Logic
import tech.kzen.lib.common.exec.logic.LogicExecutionFacade
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultCancelled
import tech.kzen.lib.common.exec.logic.model.LogicResultFailed
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.server.exec.logic.context.MutableLogicControl
import tech.kzen.lib.server.exec.logic.context.MutableLogicResourceScope
import java.util.concurrent.ConcurrentHashMap


/**
 * The run-scoped [JobLogicHost] handed to every Worker (via [JobControlImpl]); only nested-Logic Workers
 * (e.g. [tech.kzen.auto.server.objects.job.worker.RunWorker]) call it.
 *
 * Concurrency design — the crux of the Job nested-Logic problem. A top-level Script / Flow drives its child
 * frames on the SINGLE [tech.kzen.auto.server.service.impl.ServerLogicController] execution thread, sharing
 * ONE [MutableLogicControl] whose stepping state (`frameDepth`, step budget, step-out target) is a single
 * linear call-spine. A Job runs its Workers concurrently on the supervisor pool, so several Workers may host
 * children at the same instant — sharing that one steppable control would corrupt its frame/step counters
 * and entangle unrelated children's pause semantics.
 *
 * Resolution: CONFINEMENT, not shared-state locking. Each [run] builds its child on a PRIVATE
 * [MutableLogicControl] + [MutableLogicResourceScope] (never the run's shared control / scope) and drives it
 * full-speed to completion. The only machinery it shares is the stateless [GraphCreator] (a pure function of
 * its immutable inputs) and the immutable [graphDefinition], so concurrent children are fully isolated — real
 * pipeline parallelism, no cross-child interference, and none of the Job-vs-Script pause-semantics mismatch.
 *
 * A child runs against the SAME live [graphDefinition] the Job launched with (the full successful definition,
 * which still contains the child's own document), resolved per call via `filterTransitive(child.documentPath)`.
 * [cancelAll] (run teardown / cancel) flips every in-flight child's private control to Cancel so it unwinds.
 *
 * P2 scope: a child is NOT registered as a frame in the run's sidebar tree and its internal trace is dropped
 * ([NoOpLogicTraceHandle]); and a child may not itself start a further nested Logic ([NestedLogicUnsupported]).
 * Both are deferred refinements, not load-bearing for running a child per event.
 */
class JobLogicHostImpl(
    private val graphDefinition: GraphDefinition,
    private val runExecutionId: LogicRunExecutionId,
    private val graphCreator: GraphCreator,
    private val environment: GraphEnvironment
):
    JobLogicHost
{
    //-----------------------------------------------------------------------------------------------------------------
    // Live children's private controls, so cancelAll can abort them; a child registers on entry, deregisters
    // in its finally. Thread-safe set: children run on the concurrent supervisor pool.
    private val activeControls = ConcurrentHashMap.newKeySet<MutableLogicControl>()

    @Volatile
    private var cancelled = false


    //-----------------------------------------------------------------------------------------------------------------
    override fun run(child: ObjectLocation, input: Any?): LogicResult {
        if (cancelled) {
            return LogicResultCancelled
        }

        val control = MutableLogicControl(false)
        val resourceScope = MutableLogicResourceScope()
        activeControls.add(control)
        try {
            // A cancel that lands between the guard above and registration: honour it on this child too.
            if (cancelled) {
                control.commandCancel()
            }

            val childGraph = graphCreator.createGraph(
                graphDefinition.filterTransitive(child.documentPath), environment)

            val childLogic = childGraph[child]?.reference as? Logic
                ?: return LogicResultFailed("Not a Logic: $child")

            val childExecutionId = LogicRunExecutionId(
                runExecutionId.logicRunId, LogicExecutionId.random())

            val execution = childLogic.execute(
                NestedLogicUnsupported, NoOpLogicTraceHandle, childExecutionId, control)

            val ready = execution.beforeStart(argumentTuple(childLogic, input))
            if (! ready) {
                execution.close(false)
                return LogicResultFailed("Unable to initialize $child")
            }

            // The private control's command starts None and is only ever set to Cancel (by cancelAll), so
            // the child runs straight to a terminal result in one pass — it never returns Paused. The loop
            // is a defensive guard for a hypothetical child that pauses for some other reason.
            while (true) {
                val result = execution.continueOrStart(control, resourceScope, graphDefinition)
                if (result.isTerminal()) {
                    execution.close(result is LogicResultFailed)
                    return result
                }
            }
        }
        finally {
            activeControls.remove(control)
            resourceScope.disposeAll(false)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Abort every in-flight child (called by JobExecution teardown / cancel): each observes Cancel at its next
    // boundary and unwinds, releasing its resources. New runs after this short-circuit to Cancelled.
    fun cancelAll() {
        cancelled = true
        for (control in activeControls) {
            control.commandCancel()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun argumentTuple(childLogic: Logic, input: Any?): TupleValue {
        val firstParameter = childLogic.define().inputs.components.firstOrNull()
            ?: return TupleValue.empty
        return TupleValue(listOf(
            TupleComponentValue(firstParameter.name, input)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private object NestedLogicUnsupported: LogicHandle {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation
        ): LogicExecutionFacade =
            throw UnsupportedOperationException(
                "A Logic hosted by a Job Worker may not itself start a further nested Logic yet: " +
                    originalObjectLocation)
    }


    private object NoOpLogicTraceHandle: LogicTraceHandle {
        override fun register(callback: (LogicTraceQuery) -> Unit): AutoCloseable =
            AutoCloseable {}

        override fun set(logicTracePath: LogicTracePath, executionValue: ExecutionValue) {}

        override fun append(objectStableId: ObjectStableId, value: ExecutionValue) {}

        override fun clearAll(prefix: LogicTracePath) {}
    }
}
