package tech.kzen.auto.server.api.handler

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.server.service.exec.ModelTaskRepository
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.exec.task.model.TaskId
import tech.kzen.lib.common.exec.task.model.TaskModel


class TaskHandler(
    private val modelTaskRepository: ModelTaskRepository
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun taskSubmit(parameters: Parameters): TaskModel {
        val objectLocation = parameters.getObjectLocationParam()

        val params = mutableMapOf<String, List<String>>()
        for (e in parameters.entries()) {
            if (e.key == CommonRestApi.paramDocumentPath ||
                e.key == CommonRestApi.paramObjectPath) {
                continue
            }
            params[e.key] = e.value
        }

        val detachedRequest = ExecutionRequest(RequestParams(params), null)

        val execution: TaskModel = runBlocking {
            modelTaskRepository.submit(
                objectLocation,
                detachedRequest)
        }

        return execution
    }


    fun taskQuery(parameters: Parameters): TaskModel? {
        val taskId: TaskId = parameters
            .getParam(CommonRestApi.paramTaskId) { TaskId(it) }

        return runBlocking {
            modelTaskRepository.query(taskId)
        }
    }


    fun taskCancel(parameters: Parameters): TaskModel? {
        val taskId: TaskId = parameters
            .getParam(CommonRestApi.paramTaskId) { TaskId(it) }

        return runBlocking {
            modelTaskRepository.cancel(taskId)
        }
    }


    fun taskLookup(parameters: Parameters): List<String> {
        val objectLocation = parameters.getObjectLocationParam()

        val tasks: Set<TaskId> = runBlocking {
            modelTaskRepository.lookupActive(objectLocation)
        }

        return tasks.map { it.identifier }
    }
}
