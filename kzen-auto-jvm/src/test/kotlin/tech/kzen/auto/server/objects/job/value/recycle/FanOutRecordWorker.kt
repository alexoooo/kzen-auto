package tech.kzen.auto.server.objects.job.value.recycle

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.ExpandingTransformWorker
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * Test expanding transform: each input record becomes [fanOut] texts `"<id>-<k>"`, the record's id read afresh
 * before every output — so a record the channel let go of mid-expansion (and its pool recycled) fails by name.
 * Its per-element cursor migrates, so a live edit resumes the expansion where it stopped.
 */
@Reflect
class FanOutRecordWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    private val fanOut: Int,
    selfLocation: ObjectLocation
):
    ExpandingTransformWorker(input, output, selfLocation)
{
    // Outputs of the active element already sent, claimed before each send like a source's index
    @Volatile
    private var emitted = 0


    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        while (emitted < fanOut) {
            val k = emitted
            emitted += 1
            val id = element.access.readLong(element.access.field(element.root, PooledRecord.idField))
            emit.send(JobDataValues.lift("$id-$k"))
        }
        emitted = 0
    }


    override fun captureExpansionState(): Any = emitted


    override fun loadExpansionState(captured: Any?) {
        emitted = captured as? Int ?: 0
    }
}
