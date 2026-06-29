package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.CancellationException
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.logic.model.LogicPauseReason
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
 * [tech.kzen.auto.common.paradigm.job.api.JobLogicHost] (from [JobControl.logicHost]) exactly like RunStep
 * drives its callee: `logicHandleFacade().start(instructions)` confines the child to its OWN control, then
 * `beforeStart` → `continueOrStart`* → `close`. At full speed one `continueOrStart` runs the child to
 * completion; while the Job is stepping/paused the child's command (delegated from the run's shared control)
 * is Pause and the driver grants its control one fresh-step budget per wavefront, so each `continueOrStart`
 * advances one boundary (descending INTO the child) and returns Paused — this Worker then parks at a
 * [JobControl.checkpoint] until the next wavefront, staying quiescence-visible. Each call is wrapped in
 * [JobControl.runBlockingIo] so it is counted; a cancelling Job surfaces as [LogicResultCancelled].
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
        val host = control.logicHost()
        val facade = host.logicHandleFacade().start(instructions)
        try {
            val initialized = control.runBlockingIo {
                facade.beforeStart(host.argumentTuple(instructions, batch))
            }
            if (! initialized) {
                throw IllegalStateException("Unable to initialize $instructions")
            }

            while (true) {
                val result = control.runBlockingIo {
                    facade.continueOrStart(host.graphDefinition())
                }

                when (result) {
                    is LogicResultSuccess -> {
                        ran += 1
                        emit.send(result.value.mainComponentValue())
                        return
                    }

                    // The child parked rather than finishing. The result's reason says why: a plain Boundary
                    // settle is the normal step wavefront (the child advanced one fresh boundary), while a
                    // deliberate halt — a Pause step (Explicit) or a recoverable failure under pause-on-error
                    // (Error) — should halt the whole Job for inspect / fix + resume rather than busy-looping
                    // the same boundary. requestHalt carries that reason up; a Boundary settle never halts.
                    // Either way this Worker then parks (staying quiescence-visible) until the next wavefront /
                    // resume, then drives the SAME child onward (its facade stays open; the driver re-grants
                    // its budget).
                    is LogicResultPaused -> {
                        if (result.reason != LogicPauseReason.Boundary) {
                            control.requestHalt(result.reason)
                        }
                        control.checkpoint()
                    }

                    // The Job is cancelling and the child observed it: unwind the Worker.
                    LogicResultCancelled ->
                        throw CancellationException("Job cancelled")

                    is LogicResultFailed ->
                        throw IllegalStateException("Run failed ($instructions): ${result.message}")
                }
            }
        }
        finally {
            facade.close()
        }
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("ran" to ran)
}
