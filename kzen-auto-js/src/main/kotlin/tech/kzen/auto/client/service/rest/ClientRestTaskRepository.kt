package tech.kzen.auto.client.service.rest

import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.auto.common.paradigm.task.service.TaskRepository
import tech.kzen.lib.common.model.location.ObjectLocation


class ClientRestTaskRepository(
        private val restClient: ClientRestApi
):
    TaskRepository
{
    override suspend fun submit(taskLocation: ObjectLocation, request: ExecutionRequest): TaskModel {
        val params = request.parameters.values.flatMap { e -> e.value.map { e.key to it } }
        return restClient.taskSubmit(taskLocation, *params.toTypedArray())
    }


    override suspend fun query(taskId: TaskId): TaskModel? {
        return restClient.taskQuery(taskId)
    }


    override suspend fun cancel(taskId: TaskId): TaskModel? {
        return restClient.taskCancel(taskId)
    }


    override suspend fun lookupActive(taskLocation: ObjectLocation): Set<TaskId> {
        return restClient.taskLookup(taskLocation)
    }
}