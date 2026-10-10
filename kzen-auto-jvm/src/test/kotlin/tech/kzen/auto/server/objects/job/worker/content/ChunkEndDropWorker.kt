package tech.kzen.auto.server.objects.job.worker.content

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.TransformWorker
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * Test pass-through ahead of `Write`: forwards every chunk but those that end an output, as a producer that never
 * marks an output's end would send them.
 */
@Reflect
class ChunkEndDropWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
):
    TransformWorker(input, output, selfLocation)
{
    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        if (!(JobDataValues.native(element) as Bytes).endsOutput()) {
            emit.send(element)
        }
    }
}
