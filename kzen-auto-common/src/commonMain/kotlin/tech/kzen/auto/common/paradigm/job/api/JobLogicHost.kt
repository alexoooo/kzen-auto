package tech.kzen.auto.common.paradigm.job.api

import tech.kzen.lib.common.exec.logic.LogicHandleFacade
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * Run-scoped seam a Worker uses to invoke ANOTHER Logic (a Script / Flow / Job) as a child, once per event —
 * the Job analogue of a Script's Run step / a Flow's Run-Logic vertex. Obtained from
 * [tech.kzen.auto.common.paradigm.job.control.JobControl.logicHost]; only nested-Logic Workers (e.g.
 * [RunWorker][tech.kzen.auto.server.objects.job.worker.RunWorker]) need it.
 *
 * A Worker drives a child exactly like a Script's RunStep drives its callee:
 * [logicHandleFacade]`.start(child)` → [LogicExecutionFacade][tech.kzen.lib.common.exec.logic.LogicExecutionFacade]
 * → `beforeStart(`[argumentTuple]`)` → `continueOrStart(`[graphDefinition]`)`* → `close()`. Each child runs on
 * its OWN control (the host confines it), so the concurrently-running Workers of a Job host children in parallel
 * without interfering, and a Job Step descends into a child via that child's own control — no shared stepping
 * state. The child is first-class: it may itself start a FURTHER nested Logic, recursing to arbitrary depth,
 * trace-recorded and mirrored into the run's frame tree.
 */
interface JobLogicHost {
    /**
     * Starts a child Logic and drives it (`start` → `beforeStart` → `continueOrStart`* → `close`). Each
     * `start` confines its child to a fresh private control, so concurrent children never interfere.
     */
    fun logicHandleFacade(): LogicHandleFacade


    /** The live (full) definition to pass to the child's `continueOrStart` (resolves the child's document). */
    fun graphDefinition(): GraphDefinition


    /** Wraps [input] as the child's first declared parameter (or empty if it declares none), for `beforeStart`. */
    fun argumentTuple(child: ObjectLocation, input: Any?): TupleValue
}
