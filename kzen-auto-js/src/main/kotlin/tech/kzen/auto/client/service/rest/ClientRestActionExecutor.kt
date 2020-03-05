package tech.kzen.auto.client.service.rest

import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.control.ControlTransition
import tech.kzen.auto.common.paradigm.imperative.service.ActionExecutor
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation


class ClientRestActionExecutor(
        private val restClient: ClientRestApi
): ActionExecutor {
    override suspend fun execute(
            host: DocumentPath,
            actionLocation: ObjectLocation,
            imperativeModel: ImperativeModel
    ): ExecutionResult {
        return try {
            restClient.performAction(host, actionLocation).executionResult!!
        }
        catch (e: Exception) {
            ExecutionFailure(e.message ?: "Error")
        }
    }


    override suspend fun control(
            host: DocumentPath,
            actionLocation: ObjectLocation,
            imperativeModel: ImperativeModel
    ): ControlTransition {
        return restClient.performAction(host, actionLocation).controlTransition!!
//        return try {
//            restClient.performControlFlow(actionLocation).controlTransition!!
//        }
//        catch (e: Exception) {
//            ImperativeError(e.message ?: "Error")
//        }
    }
}