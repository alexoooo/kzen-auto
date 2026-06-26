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
 * The trace / argument resolution reuses one execution id and one parameter-name lookup per nested DOCUMENT for
 * the whole run ([nestedExecutionId] / [firstParameterNames]), not per element: a Job streams arbitrarily many
 * elements through one run, so a fresh id / rebuild per element would leak a buffer (and re-resolve a constant)
 * per element. Two distinct Run Workers pointing at the SAME child document share one trace buffer (keyed by
 * document, not caller), so their concurrent writes interleave (cosmetic, last-write-wins) — deferred.
 */
class JobLogicHostImpl(
    private val fullDefinition: GraphDefinition,
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
    // Every live child's control: the driver arms them on a Step (grantStepToChildren) and cancelAll aborts
    // them; a child registers on start, deregisters when its facade closes. Thread-safe: children run on the
    // concurrent supervisor pool.
    private val childControls = ConcurrentHashMap.newKeySet<MutableLogicControl>()

    // One trace execution id per nested document, reused for the whole Job run (see KDoc).
    private val nestedExecutionIds = ConcurrentHashMap<ObjectLocation, LogicRunExecutionId>()

    // Memoized first-declared-input name per child document (constant for the run), so argumentTuple needn't
    // rebuild the child graph per element just to name the positional input.
    private val firstParameterNames = ConcurrentHashMap<ObjectLocation, FirstParameterName>()

    @Volatile
    private var cancelled = false

    private val handleFacade = LogicHandleFacade(runExecutionId, TopLevelHandle())


    //-----------------------------------------------------------------------------------------------------------------
    override fun logicHandleFacade(): LogicHandleFacade {
        return handleFacade
    }


    override fun graphDefinition(): GraphDefinition {
        return fullDefinition
    }


    override fun argumentTuple(child: ObjectLocation, input: Any?): TupleValue {
        val parameterName = firstParameterNames
            .computeIfAbsent(child) { FirstParameterName(resolveFirstParameterName(it)) }
            .value
            ?: return TupleValue.empty
        return TupleValue(listOf(TupleComponentValue(parameterName, input)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Arm every live child with one fresh-step budget: called by JobExecution on a Step tick (before releasing
    // the wavefront), so each Worker's child advances exactly one boundary (descending into it).
    fun grantStepToChildren() {
        for (control in childControls) {
            control.arm(1)
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
    private fun nestedExecutionId(location: ObjectLocation): LogicRunExecutionId =
        nestedExecutionIds.computeIfAbsent(location) {
            LogicRunExecutionId(runExecutionId.logicRunId, LogicExecutionId.random())
        }


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
            val control = MutableLogicControl(false, sharedControl::pollCommand)
            if (cancelled) {
                control.commandCancel()
            }
            val resourceScope = MutableLogicResourceScope()
            childControls.add(control)

            val childExecutionId = nestedExecutionId(originalObjectLocation)

            var frameHandle: AutoCloseable? = null
            val listener = object: LogicExecutionListener {
                override fun closed() {
                    frameHandle?.close()
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
            val childExecutionId = nestedExecutionId(originalObjectLocation)

            var frameHandle: AutoCloseable? = null
            val listener = object: LogicExecutionListener {
                override fun closed() {
                    frameHandle?.close()
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
