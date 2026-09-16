package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * Forwards the first [count] elements unchanged and then COMPLETES (content streaming spike CS2,
 * docs/plans/2026-09-16_borrowed-elements.md): an ordinary [TransformWorker] whose only distinction is
 * that it ends its own drive loop via [requestCompletion] rather than waiting for its input to end, which is
 * what makes its upstream see a closed downstream
 * ([tech.kzen.auto.server.objects.job.channel.DownstreamClosedException])
 * instead of a paused one. `count <= 0` completes on the first element.
 */
@Reflect
class TakeWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    private val count: Int,
    selfLocation: ObjectLocation
):
    TransformWorker(input, output, selfLocation)
{
    private var taken = 0L


    override suspend fun onStart(control: JobControl) {
        if (count <= 0) {
            requestCompletion()
        }
    }


    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        emit.send(element)
        taken += 1
        if (taken >= count) {
            requestCompletion()
        }
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("taken" to taken)
}
