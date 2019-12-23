package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ScriptStep
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation


@Suppress("unused")
class DisplayValue(
        private val text: ObjectLocation
): ScriptStep {
    override suspend fun perform(
            imperativeModel: ImperativeModel,
            graphInstance: GraphInstance
    ): ExecutionResult {
        val frame = imperativeModel.findLast(text)
        val state = frame?.states?.get(text.objectPath)
        val result = state?.previous as? ExecutionSuccess
        val value = result?.value ?: NullExecutionValue

        return ExecutionSuccess(
                NullExecutionValue,
                value)
    }
}