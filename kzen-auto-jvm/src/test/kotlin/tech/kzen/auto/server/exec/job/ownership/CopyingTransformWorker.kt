package tech.kzen.auto.server.exec.job.ownership

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.worker.JavaTransformWorker
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * A [JavaTransformWorker] that projects each owned [CloseCountingResource] to a row of scalars (its name and
 * the name's length) — the plain-Java analytical shape (a per-symbol tally read off a native model). With
 * [independent] it declares the rows deliberate copies ([independentOutputs]), so the element closes when the
 * callback returns and a Sort downstream retains only rows; without it the default conservative inheritance
 * applies and the Sort retains the natives through the rows.
 */
@Reflect
class CopyingTransformWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    private val independent: Boolean,
    selfLocation: ObjectLocation
):
    JavaTransformWorker(input, output, selfLocation)
{
    override fun onElementBlocking(element: Any?, control: JobControl): Iterator<*> {
        val resource = element as CloseCountingResource
        return listOf(mapOf("name" to resource.name, "length" to resource.name.length)).iterator()
    }


    override fun independentOutputs(): Boolean = independent
}
