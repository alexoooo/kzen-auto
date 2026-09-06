package tech.kzen.auto.server.objects.job.worker.javafixture

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.SinkWorker
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.CopyOnWriteArrayList


/** Test sink recording every element's native form, so a Job over the Java adapters can be asserted end to end. */
@Reflect
class CollectingSinkWorker(
    input: ChannelInput<*>,
    selfLocation: ObjectLocation
):
    SinkWorker(input, selfLocation)
{
    companion object {
        val received = CopyOnWriteArrayList<Any?>()

        fun reset() {
            received.clear()
        }
    }


    override suspend fun onElement(element: DataValue, control: JobControl) {
        received.add(JobDataValues.boundary(element))
    }
}
