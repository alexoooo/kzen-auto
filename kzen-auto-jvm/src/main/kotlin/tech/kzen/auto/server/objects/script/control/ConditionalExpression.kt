package tech.kzen.auto.server.objects.script.control

import tech.kzen.auto.common.paradigm.common.model.BooleanExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeError
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import tech.kzen.lib.common.model.locate.ObjectLocation


@Suppress("unused")
class ConditionalExpression(
        private val condition: ObjectLocation,
        private val then: ObjectLocation,
        private val `else`: ObjectLocation
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        val conditionResult = result(condition, imperativeModel) as? ImperativeSuccess
        val conditionValue = (conditionResult?.value as? BooleanExecutionValue)?.value ?: false

        return if (conditionValue) {
            result(then, imperativeModel)
                    ?: ImperativeError("'then' missing")
        }
        else {
            result(`else`, imperativeModel)
                    ?: ImperativeError("'else' missing")
        }
    }


    private fun result(
            dependency: ObjectLocation,
            imperativeModel: ImperativeModel
    ): ImperativeResult? {
        val conditionFrame = imperativeModel.findLast(dependency)
        val conditionState = conditionFrame?.states?.get(dependency.objectPath)
        return conditionState?.previous
    }
}