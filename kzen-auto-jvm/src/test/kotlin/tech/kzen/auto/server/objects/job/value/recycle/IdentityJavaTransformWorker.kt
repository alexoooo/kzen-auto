package tech.kzen.auto.server.objects.job.value.recycle

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.worker.JavaTransformWorker
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/** Test plain-Java transform answering each element with itself: plugin code that may keep what it was given. */
@Reflect
class IdentityJavaTransformWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
):
    JavaTransformWorker(input, output, selfLocation)
{
    override fun onElementBlocking(element: Any?, control: JobControl): Iterator<*> =
        listOf(element).iterator()
}
