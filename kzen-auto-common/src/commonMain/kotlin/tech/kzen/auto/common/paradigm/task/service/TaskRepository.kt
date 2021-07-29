package tech.kzen.auto.common.paradigm.task.service

import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.lib.common.model.locate.ObjectLocation


interface TaskRepository {
    suspend fun submit(
        taskLocation: ObjectLocation,
        request: ExecutionRequest
    ): TaskModel


    suspend fun query(
        taskId: TaskId
    ): TaskModel?


    /**
     * @param taskId obtained by submitting the task, or looking up active tasks
     * @return if taskId is active (running) or has recently terminated,
     *      null if it either never ran or terminated too long ago
     */
    suspend fun cancel(
        taskId: TaskId
    ): TaskModel?


    suspend fun lookupActive(
        taskLocation: ObjectLocation
    ): Set<TaskId>
}