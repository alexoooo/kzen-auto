package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.CancellationException
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.logic.model.LogicResultCancelled
import tech.kzen.lib.common.exec.logic.model.LogicResultFailed
import tech.kzen.lib.common.exec.logic.model.LogicResultPaused
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * Invokes another Logic ([instructions] — a Script / Flow / Job) as a child, once per incoming element:
 * passes the element as the child's first parameter and emits the child's main result downstream. The Job
 * analogue of a Script's Run step ([tech.kzen.auto.server.objects.script.step.control.RunStep]) and a Flow's
 * Run-Logic vertex ([tech.kzen.auto.server.objects.flow.vertex.RunLogicVertex]) — the seam that lets a Job
 * compose reusable sub-Logics into its dataflow rather than only built-in stages.
 *
 * Element-agnostic (the channel carries `Any?`): unlike the typed RecordBatch stages, what flows is whatever
 * the child consumes / produces. The child is driven through the run-scoped
 * [tech.kzen.auto.common.paradigm.job.api.JobLogicHost] obtained from [JobControl.logicHost], which runs each
 * child to completion on a PRIVATE control — so the concurrently-running Workers of a Job host their children
 * in parallel without interfering (see [tech.kzen.auto.server.objects.job.JobLogicHostImpl]). The blocking
 * host call is wrapped in [JobControl.runBlockingIo] so the Worker stays visible to quiescence while a child
 * runs; a cancelling Job aborts the in-flight child (surfaced here as [LogicResultCancelled]).
 *
 * A [TransformWorker]: the framework owns the drain loop, per-element checkpoint, throttled progress, and
 * end-of-stream close propagation; this Worker only maps each element through its child Logic.
 */
@Reflect
class RunWorker(
    input: ChannelInput<Any?>,
    output: ChannelOutput<Any?>,

    private val instructions: ObjectLocation,
    selfLocation: ObjectLocation
):
    TransformWorker<Any?, Any?>(input, output, selfLocation)
{
    private var ran = 0L


    override suspend fun onBatch(batch: Any?, emit: Emitter<Any?>, control: JobControl) {
        val result = control.runBlockingIo {
            control.logicHost().run(instructions, batch)
        }

        when (result) {
            is LogicResultSuccess -> {
                ran += 1
                emit.send(result.value.mainComponentValue())
            }

            // The Job is cancelling and the host aborted this in-flight child: unwind the Worker.
            LogicResultCancelled ->
                throw CancellationException("Job cancelled")

            is LogicResultFailed ->
                throw IllegalStateException("Run failed ($instructions): ${result.message}")

            LogicResultPaused ->
                throw IllegalStateException("Child logic paused unexpectedly: $instructions")
        }
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("ran" to ran)
}
