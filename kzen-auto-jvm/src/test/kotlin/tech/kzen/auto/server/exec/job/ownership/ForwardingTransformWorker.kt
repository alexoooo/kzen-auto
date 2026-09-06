package tech.kzen.auto.server.exec.job.ownership

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.api.data.Borrowed
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.TransformWorker
import tech.kzen.auto.server.objects.job.worker.ownership
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.CopyOnWriteArrayList


/**
 * Test transform over owned elements, one behaviour per [mode]: forward the element unchanged; project it to
 * an unowned scalar (its name); emit a newly constructed closeable derived from it (adopted at send, inheriting
 * the input's owner as a conservative non-scalar output does); emit a Borrowed closeable child (never adopted,
 * inheriting the parent); or fail at a given element after forwarding the earlier ones.
 */
@Reflect
class ForwardingTransformWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    private val mode: String,
    selfLocation: ObjectLocation
):
    TransformWorker(input, output, selfLocation)
{
    companion object {
        @Volatile var failAtIndex = Int.MAX_VALUE
        val derived = CopyOnWriteArrayList<CloseCountingResource>()
        val seenOpen = CopyOnWriteArrayList<Boolean>()

        fun reset() {
            failAtIndex = Int.MAX_VALUE
            derived.clear()
            seenOpen.clear()
        }
    }

    private var index = 0


    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        val resource = JobDataValues.native(element) as CloseCountingResource
        seenOpen += !resource.isClosed
        val current = index
        index += 1
        if (current >= failAtIndex) {
            throw IllegalStateException("transform failed at element $current")
        }
        when (mode) {
            "forward" -> emit.send(element)

            "scalar" -> emit.send(JobDataValues.lift(resource.name))

            "derived" -> {
                val child = CloseCountingResource("derived-${resource.name}")
                derived += child
                val output = JobDataValues.lift(child)
                control.ownership()?.inherit(output, element)
                emit.send(output)
            }

            // A closeable child the parent's own close cascades to: declared Borrowed, never closed by kzen
            "borrowed" -> {
                val child = CloseCountingResource("borrowed-${resource.name}")
                derived += child
                val output = JobDataValues.lift(Borrowed.of(child))
                control.ownership()?.inherit(output, element)
                emit.send(output)
            }

            else -> throw IllegalArgumentException("Unknown mode: $mode")
        }
    }
}
