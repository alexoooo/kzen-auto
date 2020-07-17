package tech.kzen.auto.common.objects.document.script.invoke

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.imperative.api.ScriptStep
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class InvokeInput: ScriptStep {
    override suspend fun perform(
        imperativeModel: ImperativeModel,
        graphInstance: GraphInstance
    ): ExecutionResult {
        TODO("Not yet implemented")
    }
}