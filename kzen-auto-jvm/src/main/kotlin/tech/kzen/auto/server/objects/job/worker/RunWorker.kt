package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * Invokes another Logic ([instructions] — a Script / Flow / Job) as a child, once per incoming element: the
 * element is passed as the child's first parameter and the child's main result is emitted downstream. The Job
 * analogue of a Script's Run step ([tech.kzen.auto.server.objects.script.step.control.RunStep]) and a Flow's
 * Run-Logic vertex ([tech.kzen.auto.server.objects.flow.vertex.RunLogicVertex]) — the seam that lets a Job
 * compose reusable sub-Logics into its dataflow rather than only built-in stages.
 *
 * Element-agnostic (the channel carries `Any?`): unlike the typed RecordBatch stages, what flows is whatever
 * the child consumes / produces. A [TransformWorker], so the framework owns the drain loop, per-element
 * [JobControl.checkpoint], throttled progress, and end-of-stream close propagation; this Worker only maps each
 * element through its child Logic via [JobControl.host].
 *
 * Stepping and pause-on-error are engine-driven and need no code here: [JobControl.host] hosts the child under
 * this Worker's own engine node, so Step Into descends into the child and a child that halts (a Pause step, or a
 * step parked under pause-on-error for fix + resume) leaves the host call suspended and pauses the whole Job at
 * a quiescent wavefront — the old re-entrant driver's `continueOrStart` / `requestHalt` loop collapses into the
 * single [JobControl.host] call.
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
        val result = control.host(instructions, batch)
        ran += 1
        emit.send(result.mainComponentValue())
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("ran" to ran)
}
