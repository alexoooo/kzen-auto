package tech.kzen.auto.server.objects.job.worker.content

import kotlinx.coroutines.awaitCancellation
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.common.paradigm.job.control.ValueLease
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.TransformWorker
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch


/**
 * Test-only Workers downstream of an `Extract` transform (docs/plans/2026-09-16_borrowed-elements.md BE1): each
 * consumes an entry inside its callback in a way the borrowed-element protocol must handle, and emits nothing.
 * Declared in `test/job/content/content-test-workers.yaml`.
 */

/** Keeps the previous entry's [Content] and reads it on the next entry: must fail by name (design §6.2). */
@Reflect
class StashingWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
): TransformWorker(input, output, selfLocation) {
    companion object {
        @Volatile var stashed: Content? = null
        @Volatile var readFailure: Throwable? = null

        fun reset() {
            stashed = null
            readFailure = null
        }
    }

    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        val previous = stashed
        if (previous != null) {
            try {
                previous.open().use { it.read(ByteArray(8), 0, 8) }
            }
            catch (e: Throwable) {
                readFailure = e
                throw e
            }
        }
        stashed = (JobDataValues.native(element) as Entry).content
    }
}


/** Takes a named hold on the entry past its callback: the control must refuse it by name (§3.3). */
@Reflect
class HoldingWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
): TransformWorker(input, output, selfLocation) {
    companion object {
        val leases = mutableListOf<ValueLease>()
        @Volatile var seen = 0

        fun reset() {
            leases.forEach { it.release() }
            leases.clear()
            seen = 0
        }
    }

    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        seen += 1
        leases.add(control.retain(element))
    }
}


@Reflect
class FailingWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
): TransformWorker(input, output, selfLocation) {
    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        throw IllegalStateException("injected failure on '${(JobDataValues.native(element) as Entry).name}'")
    }
}


/** Reads part of the entry, signals, then suspends until cancelled — cancellation lands inside the entry. */
@Reflect
class BlockingWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
): TransformWorker(input, output, selfLocation) {
    companion object {
        @Volatile var entered = CountDownLatch(1)

        fun reset() {
            entered = CountDownLatch(1)
        }
    }

    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        val entry = JobDataValues.native(element) as Entry
        control.runBlockingIo {
            entry.content.open().read(ByteArray(16), 0, 16)
        }
        entered.countDown()
        awaitCancellation()
    }
}


/** Records the `label` column of each record derived from an entry (a Formula's output, which inherits its owners). */
@Reflect
class LabelsWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
): TransformWorker(input, output, selfLocation) {
    companion object {
        val labels = CopyOnWriteArrayList<Any?>()

        fun reset() {
            labels.clear()
        }
    }

    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        val projection = JobDataValues.projection(element)
        val index = (0 until projection.size).first { projection.field(it).name == "label" }
        labels.add(projection.readText(index))
    }
}
