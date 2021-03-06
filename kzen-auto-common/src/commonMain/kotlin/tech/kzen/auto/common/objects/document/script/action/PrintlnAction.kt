package tech.kzen.auto.common.objects.document.script.action

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.imperative.api.ScriptStep
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class PrintlnAction(
        private val message: String
): ScriptStep {
    override suspend fun perform(
            imperativeModel: ImperativeModel,
            graphInstance: GraphInstance
    ): ExecutionResult {
        println("PrintlnAction: $message")
        return ExecutionSuccess.empty
    }
}