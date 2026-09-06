package tech.kzen.auto.server.exec.job.ownership

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.TransformWorker
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/** Test fan-out: forwards every element to its primary output and to a [second] channel (two holds per element). */
@Reflect
class TeeTransformWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    private val second: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
):
    TransformWorker(input, output, selfLocation)
{
    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        emit.send(element)
        second.send(element)
        second.flush()
    }


    override suspend fun onClose() {
        second.close()
    }
}
