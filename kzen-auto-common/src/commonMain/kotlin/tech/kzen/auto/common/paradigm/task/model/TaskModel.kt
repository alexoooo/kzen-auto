package tech.kzen.auto.common.paradigm.task.model

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.lib.common.model.locate.ObjectLocation


data class TaskModel(
    val taskId: TaskId,
    val taskLocation: ObjectLocation,
    val request: ExecutionRequest,
    val state: TaskState,
    val partialResult: ExecutionSuccess?,
    val finalResult: ExecutionResult?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val idKey = "id"
        private const val locationKey = "location"
        private const val requestKey = "request"
        private const val stateKey = "state"
        private const val partialResultKey = "partial"
        private const val finalResultKey = "result"


        @Suppress("UNCHECKED_CAST")
        fun fromJsonCollection(collection: Map<String, Any?>): TaskModel {
            return TaskModel(
                TaskId(collection[idKey] as String),
                ObjectLocation.parse(collection[locationKey] as String),
                ExecutionRequest.fromJsonCollection(collection[requestKey] as Map<String, String?>),
                TaskState.valueOf(collection[stateKey] as String),
                collection[partialResultKey]?.let { ExecutionSuccess.fromJsonCollection(it as Map<String, Any?>) },
                collection[finalResultKey]?.let { ExecutionResult.fromJsonCollection(it as Map<String, Any?>) }
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toJsonCollection(): Map<String, Any?> {
        return mapOf(
            idKey to taskId.identifier,
            locationKey to taskLocation.asString(),
            requestKey to request.toJsonCollection(),
            stateKey to state.name,
            partialResultKey to partialResult?.toJsonCollection(),
            finalResultKey to finalResult?.toJsonCollection()
        )
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun finalOrPartialResult(): ExecutionResult {
        return finalResult ?: partialResult!!
    }


    fun errorMessage(): String? {
        return when (finalResult) {
            is ExecutionFailure -> finalResult.errorMessage
            else -> null
        }
    }


    fun taskProgress(): TaskProgress? {
        val result = finalOrPartialResult() as? ExecutionSuccess
            ?: return null

        @Suppress("UNCHECKED_CAST")
        val resultDetail = result.detail.get()
            ?: return null

        return TaskProgress(resultDetail)
    }


    fun requestAction(): String {
        return request.parameters.get(ReportConventions.actionParameter)!!
    }
}