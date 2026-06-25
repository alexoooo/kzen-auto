package tech.kzen.auto.common.paradigm.job.api

import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * Run-scoped handle a Worker uses to invoke ANOTHER Logic (a Script / Flow / Job) as a child, once per
 * event — the Job analogue of a Script's Run step / a Flow's Run-Logic vertex. Obtained from
 * [tech.kzen.auto.common.paradigm.job.control.JobControl.logicHost]; only nested-Logic Workers (e.g.
 * [RunWorker][tech.kzen.auto.server.objects.job.worker.RunWorker]) need it.
 *
 * Each [run] is independent and ISOLATED: the child executes to completion full-speed on its own private
 * control, so the concurrently-running Workers of a Job may host children in parallel without interfering —
 * unlike the single-spine step machinery a top-level Script / Flow shares (one `frameDepth` / step budget
 * across the frame tree). A cancelling Job aborts in-flight children.
 */
interface JobLogicHost {
    /**
     * Runs [child] to completion, passing [input] as the child's first declared parameter (or no argument
     * if it declares none), and returns the child's terminal [LogicResult] (its main result on success).
     *
     * Blocking: invoke inside [tech.kzen.auto.common.paradigm.job.control.JobControl.runBlockingIo] so the
     * Worker stays visible to the Job's quiescence detection while the child runs.
     */
    fun run(child: ObjectLocation, input: Any?): LogicResult
}
