package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import tech.kzen.auto.server.service.ServerContext


@Suppress("unused")
class CloseBrowser: ExecutionAction {
    override suspend fun perform(): ImperativeResult {
        ServerContext.webDriverContext.quit()
        return ImperativeSuccess.empty
    }
}