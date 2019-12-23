package tech.kzen.auto.server.objects.script.browser

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.imperative.api.ScriptStep
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.lib.common.model.instance.GraphInstance


@Suppress("unused")
class CloseBrowser: ScriptStep {
    override suspend fun perform(
            imperativeModel: ImperativeModel,
            graphInstance: GraphInstance
    ): ExecutionResult {
        ServerContext.webDriverContext.quit()
        return ExecutionSuccess.empty
    }
}