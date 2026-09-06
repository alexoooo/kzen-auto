package tech.kzen.auto.server.exec.job.ownership

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.worker.CursorSourceWorker
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.toDataContract
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect


/**
 * A cursor source whose declared element contract (an integer scalar) does not fit the closeable objects it
 * pulls: the lift fails after the pull, so the acquired item must be closed and the lift error must be the
 * run's failure.
 */
@Reflect
class ContractMismatchSource(
    output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
):
    CursorSourceWorker(output, selfLocation)
{
    companion object {
        val pulled = mutableListOf<CloseCountingResource>()

        fun reset() {
            pulled.clear()
        }
    }


    // Lazily constructed: only what the framework pulls exists
    override fun open(control: JobControl): Iterator<*> =
        (0 until 2).asSequence().map { CloseCountingResource("m$it").also { pulled += it } }.iterator()


    override fun elementContract(): DataContract = TypeMetadata.int.toDataContract()
}
