package tech.kzen.auto.server.objects

import tech.kzen.auto.common.paradigm.imperative.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionResult
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionSuccess
import tech.kzen.auto.server.service.ServerContext


@Suppress("unused")
class CloseBrowser: ExecutionAction {
    override suspend fun perform(): ExecutionResult {
        ServerContext.webDriverContext.quit()
        return ExecutionSuccess.empty
    }
}