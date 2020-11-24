package tech.kzen.auto.server.objects.process

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.reactive.TaskProgress
import tech.kzen.auto.common.paradigm.task.api.TaskHandle


class ReportProgressListener(
    private val handle: TaskHandle,
    initialProgress: TaskProgress
) {
    var nextProgress: TaskProgress = initialProgress

    fun update(file: String, message: String) {
        nextProgress = nextProgress.update(
            file, message)

        handle.update { previous ->
            previous!!.withDetail(ExecutionValue.of(nextProgress.toCollection()))
        }
    }
}