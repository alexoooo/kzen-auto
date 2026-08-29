package tech.kzen.auto.server.objects.job.worker.data

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.model.location.ObjectLocation


class WorkerDataContext(
    private val control: JobControl
): DataContext {
    override fun argument(name: String): Any? {
        return control.parameter(name)
    }


    override suspend fun host(instructions: ObjectLocation, arguments: DataBindings): DataBindings {
        return control.host(instructions, arguments)
    }


    override suspend fun <R> blocking(block: () -> R): R {
        return control.runBlockingIo(block)
    }
}
