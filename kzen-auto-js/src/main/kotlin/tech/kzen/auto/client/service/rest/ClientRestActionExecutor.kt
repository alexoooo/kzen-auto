package tech.kzen.auto.client.service.rest

import tech.kzen.auto.common.paradigm.imperative.model.ImperativeError
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.control.ControlTransition
import tech.kzen.auto.common.paradigm.imperative.service.ActionExecutor
import tech.kzen.lib.common.model.locate.ObjectLocation


class ClientRestActionExecutor(
        private val restClient: ClientRestApi
): ActionExecutor {
    override suspend fun execute(
            actionLocation: ObjectLocation,
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        return try {
            restClient.performAction(actionLocation).executionResult!!
        }
        catch (e: Exception) {
            ImperativeError(e.message ?: "Error")
        }
    }


    override suspend fun control(
            actionLocation: ObjectLocation,
            imperativeModel: ImperativeModel
    ): ControlTransition {
        return restClient.performAction(actionLocation).controlTransition!!
//        return try {
//            restClient.performControlFlow(actionLocation).controlTransition!!
//        }
//        catch (e: Exception) {
//            ImperativeError(e.message ?: "Error")
//        }
    }
}