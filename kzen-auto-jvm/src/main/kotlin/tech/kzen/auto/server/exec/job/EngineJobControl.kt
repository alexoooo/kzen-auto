package tech.kzen.auto.server.exec.job

import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


/**
 * The [JobControl] a single Job Worker runs against on the new engine — the per-Worker bridge from the Worker
 * SPI (which only ever touches [JobControl]) to the [Execution] of the Worker's own engine node. Each Worker is
 * hosted as its OWN confined node (see [JobRun]), so each gets its own [EngineJobControl] / [Execution]:
 * [checkpoint] parks THIS Worker independently and the engine brings every Worker to a quiescent wavefront
 * centrally. The old [tech.kzen.auto.server.objects.job.JobControlImpl]'s shared phase + release-signal are
 * therefore gone — the engine's run command IS the phase, and parking lives on the coroutine frame.
 *
 * [publishProgress] is the always-on PUSH path: it [Execution.emit]s the Worker's live progress at a reserved
 * marker address ([workerProgressAddressMarker]) so the controller's trace bridge routes it to
 * [tech.kzen.auto.common.objects.document.job.JobConventions.workerProgressPath] (the path the JS Job UI polls),
 * throttled per Worker so a per-(small-)batch publisher doesn't flood the trace history.
 *
 * [host] is the nested-Logic seam ([RunWorker][tech.kzen.auto.server.objects.job.worker.RunWorker]): it
 * compiles the child once via the run-shared [JobChildLogicHost] and hosts it under THIS Worker's own
 * [Execution] node, so the engine drives the child's stepping and pause / cancel uniformly with the rest of
 * the tree.
 */
class EngineJobControl(
    private val execution: Execution,
    private val childLogicHost: JobChildLogicHost,
    private val objectStableMapper: ObjectStableMapper
): JobControl {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Reserved emit address tagging a Worker's live-progress payload, distinct from any other value the
        // Worker node emits; the controller's trace bridge recognizes it and routes to the Worker's progress
        // trace path. Symmetric with ScriptRunContext.nextStepAddressMarker; a stable id can never collide.
        const val workerProgressAddressMarker = "\$job-progress"

        // Minimum spacing between (non-forced) progress emits per Worker, mirroring JobControlImpl — a forced
        // write (the final end-of-stream value) always lands.
        private const val progressThrottleNanos = 200_000_000L  // 200 ms
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val progressAddress = Address.of(workerProgressAddressMarker)

    // This Worker runs single-threaded on its own node coroutine, so a plain field suffices (no concurrent
    // publishProgress for one Worker).
    private var lastProgressNanos = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun checkpoint() {
        execution.checkpoint()
    }


    override suspend fun <R> runBlockingIo(block: () -> R): R {
        // Run inline on the engine dispatcher thread so the work stays counted by the CountingDispatcher (the
        // thread is occupied → inFlight stays positive), matching the old JobControlImpl: visible to quiescence.
        return block()
    }


    override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {
        val now = System.nanoTime()
        if (! force && lastProgressNanos != 0L && now - lastProgressNanos < progressThrottleNanos) {
            return
        }
        lastProgressNanos = now

        // The emit address is the Worker node's; the Worker's own stable id (the node's stableId) is what the
        // bridge keys the progress path on, so [location] (always this Worker's own location) is not needed here.
        execution.emit(progressAddress, ExecutionValue.of(value))
    }


    override suspend fun host(instructions: ObjectLocation, input: Any?): TupleValue {
        val child = childLogicHost.compile(instructions)

        // Bind the single incoming element to the child's first declared parameter (empty when it declares
        // none) — the same positional convention a Flow Run-Logic vertex uses to pass its upstream message.
        val firstParameter = child.signature().inputs.components.firstOrNull()?.name
        val arguments =
            if (firstParameter != null) {
                TupleValue(listOf(TupleComponentValue(firstParameter, input)))
            }
            else {
                TupleValue.empty
            }

        // Host under the child document's stable id (matching a Script RunStep), so the engine's execution tree
        // and its live-edit migration carry the child by the same identity across a rebuild.
        return execution.host(objectStableMapper.objectStableId(instructions), child, arguments)
    }
}
