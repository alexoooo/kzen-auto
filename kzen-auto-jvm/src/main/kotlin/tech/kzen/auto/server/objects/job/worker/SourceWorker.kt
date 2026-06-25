package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * A SOURCE Worker — produces an output stream with no input (e.g. a file reader). The framework owns
 * end-of-stream: [output] is closed when [produce] returns, fails, or is cancelled. The subclass implements
 * only [produce], emitting via [Emitter.send] and calling [publish] to surface live progress; it never closes
 * the output channel itself, so a source can never deadlock the pipeline by forgetting to.
 */
abstract class SourceWorker<Out>(
    private val output: ChannelOutput<Any?>,
    selfLocation: ObjectLocation
):
    WorkerBase(selfLocation)
{
    private val emitter = Emitter<Out>(output)


    final override suspend fun drive(control: JobControl) {
        try {
            produce(emitter, control)
        }
        finally {
            output.close()
        }
    }


    protected abstract suspend fun produce(emit: Emitter<Out>, control: JobControl)
}
