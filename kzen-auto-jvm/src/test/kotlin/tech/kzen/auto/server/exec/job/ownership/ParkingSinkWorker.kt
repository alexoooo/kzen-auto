package tech.kzen.auto.server.exec.job.ownership

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.SinkWorker
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch


/**
 * Test sink whose first callback parks inside a blocking call until released — like a writer inside a slow
 * `onElement` — so a test can cancel the run while the callback is active and observe that nothing owned
 * closes before the callback returns. Ignores interruption while parked, as a native that does not honour
 * it would.
 */
@Reflect
class ParkingSinkWorker(
    input: ChannelInput<*>,
    selfLocation: ObjectLocation
):
    SinkWorker(input, selfLocation)
{
    companion object {
        @Volatile var parked: CountDownLatch? = null
        @Volatile var proceed: CountDownLatch? = null
        val received = CopyOnWriteArrayList<String>()
        @Volatile var openWhileParked: Boolean? = null

        fun reset() {
            parked = null
            proceed = null
            received.clear()
            openWhileParked = null
        }
    }


    override suspend fun onElement(element: DataValue, control: JobControl) {
        val resource = JobDataValues.native(element) as CloseCountingResource
        received += resource.name
        val gate = proceed
        if (received.size == 1 && gate != null) {
            control.runBlockingIo {
                parked?.countDown()
                awaitUninterruptibly(gate)
                openWhileParked = !resource.isClosed
            }
        }
    }


    private fun awaitUninterruptibly(latch: CountDownLatch) {
        var interrupted = false
        while (true) {
            try {
                latch.await()
                break
            }
            catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt()
        }
    }
}
