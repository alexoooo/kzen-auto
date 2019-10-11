package tech.kzen.auto.server.objects.script.browser

import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.server.service.ServerContext


@Suppress("unused")
class CloseBrowser: ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ExecutionResult {
        ServerContext.webDriverContext.quit()
        return ExecutionSuccess.empty
    }
}