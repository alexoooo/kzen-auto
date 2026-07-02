package tech.kzen.auto.server.objects.job.worker.test

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.worker.SinkWorker
import tech.kzen.lib.common.model.location.ObjectLocation
import java.util.concurrent.CopyOnWriteArrayList


/**
 * Test-only sink Worker for [tech.kzen.auto.server.exec.job.JobRunWorkerTest]: records every element it
 * receives into the static [recorded] list, so a test can assert what a [RunWorker] emitted downstream — i.e.
 * that the child Logic ran once per element and produced the expected transformed value.
 *
 * [recorded] is static and thread-safe ([CopyOnWriteArrayList]) so it survives across the concurrent Worker
 * coroutines and a live-edit rebuild; [reset] it before each test. Registered via [RunWorkerTestModule] (no
 * `@Reflect` / KSP in the test source set).
 */
class RecordingSinkWorker(
    input: ChannelInput<Any?>,
    selfLocation: ObjectLocation
):
    SinkWorker<Any?>(input, selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val recordedInternal = CopyOnWriteArrayList<Any?>()

        fun reset() {
            recordedInternal.clear()
        }

        fun recorded(): List<Any?> {
            return recordedInternal.toList()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onElement(element: Any?, control: JobControl) {
        recordedInternal.add(element)
    }
}
