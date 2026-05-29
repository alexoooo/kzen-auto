package tech.kzen.auto.server.objects.custom.test

import tech.kzen.lib.common.exec.task.ManagedTask
import tech.kzen.lib.common.exec.task.TaskHandle
import tech.kzen.lib.common.exec.task.TaskRun
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class AdhocTask(
    private val named: AdhocNamed
): ManagedTask {
    override suspend fun start(
        request: ExecutionRequest,
        handle: TaskHandle
    ): TaskRun {
        val run = Run(handle)
        run.start()
        return run
    }


    private inner class Run(
        val handle: TaskHandle
    ): TaskRun, Thread() {
        override fun run() {
            try {
                for (i in 1 .. 60) {
                    if (handle.stopRequested()) {
                        handle.completeAsCancelled()
                        return
                    }

                    sleep(1_000)

                    val name = named.name()
                    handle.update(ExecutionSuccess.ofValue(
                        ExecutionValue.of("hi $name - $i")))
                }

                handle.complete()
            }
            catch (t: Throwable) {
                handle.terminalFailure(ExecutionFailure.ofException(t))
            }
        }

        override fun close(error: Boolean) {}
    }
}