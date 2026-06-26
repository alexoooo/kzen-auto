package tech.kzen.auto.server.objects.job

import tech.kzen.auto.common.paradigm.job.api.JobLogicHost
import tech.kzen.auto.server.service.impl.LogicExecutionFacadeImpl
import tech.kzen.auto.server.service.impl.NestedFrameRegistry
import tech.kzen.lib.common.exec.logic.Logic
import tech.kzen.lib.common.exec.logic.LogicControl
import tech.kzen.lib.common.exec.logic.LogicExecutionFacade
import tech.kzen.lib.common.exec.logic.LogicExecutionListener
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.LogicHandleFacade
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.server.exec.logic.context.MutableLogicControl
import tech.kzen.lib.server.exec.logic.context.MutableLogicResourceScope
import tech.kzen.lib.server.exec.logic.trace.LogicTraceStore
import java.util.concurrent.ConcurrentHashMap


/**
 * The run-scoped [JobLogicHost] handed to every Worker (via [JobControlImpl]); only nested-Logic Workers
 * (e.g. [tech.kzen.auto.server.objects.job.worker.RunWorker]) call it.
 *
 * Concurrency by CONFINEMENT — each child runs on its OWN [MutableLogicControl] + [MutableLogicResourceScope].
 * A Script / Flow drives all its child frames on one control (a single linear call-spine on one thread); a Job
 * runs its Workers concurrently, so a shared control's stepping state (frame depth, step budget) would race
 * across children. Giving each child its own control isolates that step state per spine, so concurrent children
 * never interfere — and a Job Step descends into a child via the child's OWN control (no shared stepping state,
 * no peek-don't-consume, no dual mode). Only the run COMMAND is shared: a child control delegates `pollCommand`
 * to [sharedControl], so a pause / resume / cancel reaches every child instantly. ([cancelAll] additionally
 * forces each in-flight child's own command to Cancel, so a teardown that does NOT cancel the shared control —
 * a migrate / deadlock — still unwinds a child blocked mid-`continueOrStart`.)
 *
 * A Worker drives a child exactly like a Script's RunStep: [logicHandleFacade]`.start(child)` confines it to a
 * fresh control, then `beforeStart(`[argumentTuple]`)` → `continueOrStart(`[graphDefinition]`)`* → `close()`.
 * On a Job Step the driver ([JobExecution]) arms each tracked child with one fresh-step budget
 * ([grantStepToChildren]) before releasing the wavefront, so each Worker advances its child one boundary.
 *
 * FULL nesting + trace + visibility. The child is a first-class nested Logic: it receives a [NestedHandle] (so
 * its own Run step can start a FURTHER nested Logic — the same handle threads down the spine via the canonical
 * [LogicExecutionFacadeImpl], recursing to arbitrary depth, on the SAME control as the child so stepping
 * descends recursively) and a REAL trace handle from [logicTraceStore] keyed under the Job's run id. Each open
 * child (recursively) is mirrored into the controller's frame tree via [nestedFrameRegistry] so the sidebar
 * shows it executing at the right depth; the facade's close listener detaches the frame, disposes the child's
 * scope, and untracks its control when it finishes.
 *
 * Each child INVOCATION gets its OWN execution id (minted per [LogicHandle.start]); its trace buffer is
 * reclaimed when the frame closes ([logicTraceStore]`.evict`). So a re-entry — the next element through the same
 * Run Worker — starts from an EMPTY buffer instead of the prior invocation's finished state (the reported
 * "already executed" bug), a streaming Job is bounded to its LIVE frames (no per-element buffer leak), and two
 * Run Workers driving the SAME child document get DISTINCT buffers (no last-write-wins interleave). Only the
 * positional first-parameter-name lookup is memoized per nested DOCUMENT ([firstParameterNames]) — a run
 * constant, so [argumentTuple] needn't rebuild the child graph per element just to name the input.
 */
class JobLogicHostImpl(
    @Volatile
    private var fullDefinition: GraphDefinition,
    private val runExecutionId: LogicRunExecutionId,
    private val graphCreator: GraphCreator,
    private val environment: GraphEnvironment,
    private val logicTraceStore: LogicTraceStore,
    private val nestedFrameRegistry: NestedFrameRegistry,
    private val sharedControl: LogicControl
):
    JobLogicHost
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // A Job's RunWorkers sit at the run-root level, so each hosted child's top frame attaches one level
        // below the Job (frame depth 1). grantStepToChildren subtracts this to translate the controller's
        // global step depth limit into the child's own frame coordinates.
        private const val childAttachDepth = 1
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Every live child's control: the driver arms them on a Step (grantStepToChildren) and cancelAll aborts
    // them; a child registers on start, deregisters when its facade closes. Thread-safe: children run on the
    // concurrent supervisor pool.
    private val childControls = ConcurrentHashMap.newKeySet<MutableLogicControl>()

    // Memoized first-declared-input name per child document (constant for the run), so argumentTuple needn't
    // rebuild the child graph per element just to name the positional input.
    private val firstParameterNames = ConcurrentHashMap<ObjectLocation, FirstParameterName>()

    @Volatile
    private var cancelled = false

    // The depth limit (in the run's GLOBAL frame coordinates) of the step currently in flight, set by
    // grantStepToChildren before each step wavefront and read when a NEW child is born mid-wavefront
    // (TopLevelHandle.start). A child created during a Step Over / Step Out must inherit the plan so it runs
    // free instead of pausing at its entry (which would descend INTO it — the bug where stepping over the
    // RunWorker's child still entered it). MAX = Step Into / plain pause (a fresh child pauses at its entry).
    @Volatile
    private var childStepDepthLimit: Int = Int.MAX_VALUE

    private val handleFacade = LogicHandleFacade(runExecutionId, TopLevelHandle())


    //-----------------------------------------------------------------------------------------------------------------
    override fun logicHandleFacade(): LogicHandleFacade {
        return handleFacade
    }


    override fun graphDefinition(): GraphDefinition {
        return fullDefinition
    }


    // Re-point the definition that children are built from at the latest (re-read) notation. A child Logic a
    // Run Worker runs is a Nominal reference, so it sits OUTSIDE the Job's own filterTransitive subset and an
    // edit to it never trips JobExecution's migrate check — without this refresh the host would keep handing
    // out the launch-time notation, so a sub-script edited while paused would not take effect on resume. A
    // child (re)built after this sees the edit (the Job's own workers / channels are still handled by migrate).
    // Safe to swap while the run is paused (workers parked, no concurrent child build); firstParameterNames is
    // cleared since an edited child's first-input name may have changed.
    fun updateDefinition(definition: GraphDefinition) {
        fullDefinition = definition
        firstParameterNames.clear()
    }


    override fun argumentTuple(child: ObjectLocation, input: Any?): TupleValue {
        val parameterName = firstParameterNames
            .computeIfAbsent(child) { FirstParameterName(resolveFirstParameterName(it)) }
            .value
            ?: return TupleValue.empty
        return TupleValue(listOf(TupleComponentValue(parameterName, input)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Arm every live child with the controller's step plan for this wavefront, translated into the child's OWN
    // frame coordinates: a Job's children run on their own controls rooted one frame-level below the Job (the
    // RunWorker -> child boundary is at depth 1 of the run's frame tree), so a global depth limit D maps to
    // D - childAttachDepth in the child. This is what makes Step Over (budget 1, finite limit) and Step Out
    // (budget 0, finite limit) cross the Job boundary instead of collapsing to Step Into / a no-op: below the
    // limit the child runs free, at it the child pauses (see LogicControl.runningFreeByDepth /
    // consumeStepBudget). An unbounded limit (Step Into / plain step) stays unbounded.
    //
    // Assumes a top-level Job, whose children attach at frame depth 1; a Job nested within another Logic would
    // need its own frame depth folded into the offset — deferred (such a Job's children degrade to Step-Into
    // descent, never worse than before). Records the plan (childStepDepthLimit) so a child BORN later in this
    // same wavefront inherits it too (TopLevelHandle.start), not only the children live at grant time.
    fun grantStepToChildren(budget: Int, depthLimit: Int) {
        childStepDepthLimit = depthLimit
        val childLimit = childDepthLimit(depthLimit)
        for (control in childControls) {
            control.arm(budget, childLimit)
        }
    }


    // Step Into: descend exactly one boundary into each child (budget 1, unbounded). The default grant on a
    // Job Step; the parameterless form keeps the common case (and the nested-logic tests) readable.
    fun grantStepToChildren() {
        grantStepToChildren(1, Int.MAX_VALUE)
    }


    // Translate a global frame depth limit into a hosted child's OWN frame coordinates (its top frame attaches
    // childAttachDepth levels below the Job root). An unbounded limit stays unbounded.
    private fun childDepthLimit(globalDepthLimit: Int): Int {
        return if (globalDepthLimit == Int.MAX_VALUE) {
            Int.MAX_VALUE
        }
        else {
            globalDepthLimit - childAttachDepth
        }
    }


    // Abort every in-flight child (called by JobExecution teardown / cancel): each observes Cancel on its own
    // control at its next boundary and unwinds, releasing its resources. New starts after this short-circuit to
    // Cancelled. (Children also observe a shared-control cancel via the delegated command; this covers the
    // teardown paths — migrate / deadlock — that do NOT cancel the shared control.)
    fun cancelAll() {
        cancelled = true
        for (control in childControls) {
            control.commandCancel()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun resolveFirstParameterName(child: ObjectLocation): TupleComponentName? {
        val childGraph = graphCreator.createGraph(
            fullDefinition.filterTransitive(child.documentPath), environment)
        val childLogic = childGraph[child]?.reference as? Logic
        return childLogic?.define()?.inputs?.components?.firstOrNull()?.name
    }


    private class FirstParameterName(val value: TupleComponentName?)


    //-----------------------------------------------------------------------------------------------------------------
    // The top-level handle a Run Worker drives: each start() confines its child to a FRESH private control +
    // scope (tracked for stepping / cancel), so concurrent children are isolated. The child's descendants get a
    // NestedHandle bound to that same control, so a deeper Run recurses on the child's own spine.
    private inner class TopLevelHandle: LogicHandle {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation
        ): LogicExecutionFacade {
            // Delegate both the run command AND pause-on-error to the shared host control: a live mid-run
            // pause-on-error toggle (set on the shared control) then reaches every concurrent child without
            // copying the flag, and a child that fails under it returns a paused result (see RunWorker).
            val control = MutableLogicControl(false, sharedControl::pollCommand, sharedControl::pauseOnError)
            if (cancelled) {
                control.commandCancel()
            }
            // Inherit the in-flight step plan: under Step Into a fresh child pauses at its entry (so the run
            // descends into it); under Step Over / Step Out its entry boundary is below the depth limit, so it
            // runs free — the RunWorker's child is stepped over without the run descending into it. Budget 0:
            // a fresh child never spends a step to run its first boundary, it either pauses at entry (limit
            // covers depth 0) or runs free by depth (limit below it).
            control.arm(0, childDepthLimit(childStepDepthLimit))
            val resourceScope = MutableLogicResourceScope()
            childControls.add(control)

            val childExecutionId = LogicRunExecutionId(runExecutionId.logicRunId, LogicExecutionId.random())

            var frameHandle: AutoCloseable? = null
            val listener = object: LogicExecutionListener {
                override fun closed() {
                    frameHandle?.close()
                    logicTraceStore.evict(childExecutionId)
                    childControls.remove(control)
                    resourceScope.disposeAll(false)
                }
            }

            val facade = LogicExecutionFacadeImpl(
                fullDefinition, control, resourceScope, listener, logicTraceStore) { environment }

            val execution = facade.open(
                childExecutionId, originalObjectLocation, NestedHandle(control, resourceScope), graphCreator)

            frameHandle = nestedFrameRegistry.attach(
                logicRunExecutionId.logicExecutionId, originalObjectLocation,
                childExecutionId.logicExecutionId, execution)

            return facade
        }
    }


    // The handle threaded into a child so its OWN Run step can start a FURTHER nested Logic — confined to the
    // current child's control + scope (so the recursion stays on the child's spine and is steppable through the
    // same control) and passing ITSELF down for arbitrary depth. Reuses the canonical LogicExecutionFacadeImpl.
    private inner class NestedHandle(
        private val control: LogicControl,
        private val resourceScope: MutableLogicResourceScope
    ):
        LogicHandle
    {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation
        ): LogicExecutionFacade {
            val childExecutionId = LogicRunExecutionId(runExecutionId.logicRunId, LogicExecutionId.random())

            var frameHandle: AutoCloseable? = null
            val listener = object: LogicExecutionListener {
                override fun closed() {
                    frameHandle?.close()
                    logicTraceStore.evict(childExecutionId)
                }
            }

            val facade = LogicExecutionFacadeImpl(
                fullDefinition, control, resourceScope, listener, logicTraceStore) { environment }

            val execution = facade.open(
                childExecutionId, originalObjectLocation, this, graphCreator)

            frameHandle = nestedFrameRegistry.attach(
                logicRunExecutionId.logicExecutionId, originalObjectLocation,
                childExecutionId.logicExecutionId, execution)

            return facade
        }
    }
}
