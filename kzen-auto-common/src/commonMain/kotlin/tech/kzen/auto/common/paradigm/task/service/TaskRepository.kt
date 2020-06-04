package tech.kzen.auto.common.paradigm.task.service

import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.lib.common.model.locate.ObjectLocation


interface TaskRepository {
    suspend fun submit(
        taskLocation: ObjectLocation,
        request: DetachedRequest
    ): TaskModel


    suspend fun query(
        taskId: TaskId
    ): TaskModel?


    suspend fun cancel(
        taskId: TaskId
    ): TaskModel?


    suspend fun lookupActive(
        taskLocation: ObjectLocation
    ): Set<TaskId>
}