package tech.kzen.auto.server.objects.job.worker.test

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.SourceWorker
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.CountDownLatch


/**
 * Test source that keeps resources through its own frame ([JobControl.retain], the primitive seam under the
 * ingress boundaries): each element is a scalar, and the resource behind it stays held by this Worker's frame
 * until the frame settles after the Worker joined — which is exactly what the teardown test must observe closing.
 */
@Reflect
class AdoptingSourceWorker(
    output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
):
    SourceWorker(output, selfLocation)
{
    companion object {
        @Volatile var count = 2
        @Volatile var failAfterAdopting = false
        @Volatile var throwOnClose = false
        @Volatile var adopted: CountDownLatch? = null
        @Volatile var proceed: CountDownLatch? = null
        val resources = mutableListOf<CloseCountingResource>()

        fun reset() {
            count = 2
            failAfterAdopting = false
            throwOnClose = false
            adopted = null
            proceed = null
            resources.clear()
        }
    }


    override suspend fun produce(emit: Emitter, control: JobControl) {
        for (index in 0 until count) {
            val resource = CloseCountingResource(
                "resource-$index",
                if (throwOnClose) IllegalStateException("close of resource-$index failed") else null)
            resources += resource
            control.retain(JobDataValues.lift(resource))
            emit.send(JobDataValues.lift(index))
        }
        // Signals from inside the offloaded call (a cancel that lands before the offload skips the block), then
        // waits like a native that ignores interruption: engine cancel interrupts the call, but the Worker only
        // joins once the call returns — which is what the teardown ordering test observes
        val latch = proceed
        if (latch == null) {
            adopted?.countDown()
        }
        else {
            control.runBlockingIo {
                adopted?.countDown()
                awaitUninterruptibly(latch)
            }
        }
        if (failAfterAdopting) {
            throw IllegalStateException("source failed after adopting")
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
