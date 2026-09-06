package tech.kzen.auto.server.exec.job.ownership

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.SinkWorker
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.CopyOnWriteArrayList


/**
 * Test sink recording, per element, what it received and whether the resource behind it (when it is one) was
 * still open at that moment — the "closes after the final holder" observation of E9. Keyed by [label] so a
 * fan-out's sinks are told apart.
 */
@Reflect
class ObservingSinkWorker(
    input: ChannelInput<*>,
    private val label: String,
    selfLocation: ObjectLocation
):
    SinkWorker(input, selfLocation)
{
    class Observation(
        val sink: String,
        val value: Any?,
        val openAtReceipt: Boolean?
    )


    companion object {
        val observations = CopyOnWriteArrayList<Observation>()

        fun reset() {
            observations.clear()
        }

        fun of(sink: String): List<Observation> = observations.filter { it.sink == sink }
    }


    override suspend fun onElement(element: DataValue, control: JobControl) {
        val native = JobDataValues.native(element)
        val open = (native as? CloseCountingResource)?.let { !it.isClosed }
        observations += Observation(label, (native as? CloseCountingResource)?.name ?: native, open)
    }
}
