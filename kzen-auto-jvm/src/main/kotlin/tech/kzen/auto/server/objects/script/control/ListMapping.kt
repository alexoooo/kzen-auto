package tech.kzen.auto.server.objects.script.control

import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.imperative.api.ScriptControl
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.control.*
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation


class ListMapping(
        private val items: ObjectLocation,
        private val steps: List<ObjectLocation>
): ScriptControl {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val itemsAttributeName = AttributeName("items")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun control(
            imperativeModel: ImperativeModel,
            controlState: ControlState
    ): ControlTransition {
        val listFrame = imperativeModel.findLast(items)!!
        val listState = listFrame.states[items.objectPath]!!
        val listResult = listState.previous!!
        val listSuccess = listResult as ExecutionSuccess
        val listValue = listSuccess.value as ListExecutionValue

        return when (controlState) {
            is InitialControlState -> {
                if (listValue.values.isEmpty()) {
                    EvaluateControlTransition
                }
                else {
                    InternalControlTransition(0, NumberExecutionValue(0.0))
                }
            }

            is InternalControlState -> {
                val listIndex = (controlState.value as NumberExecutionValue).value.toInt()

                if (listIndex >= listValue.values.size - 1) {
                    EvaluateControlTransition
                }
                else {
                    val nextIndex = NumberExecutionValue((listIndex + 1).toDouble())

//                    InternalControlTransition(0, controlState.branchIndex + 1)
                    InternalControlTransition(0, nextIndex)
                }
            }

            else ->
                throw IllegalStateException()
        }
    }


    override suspend fun perform(
            imperativeModel: ImperativeModel,
            graphInstance: GraphInstance
    ): ExecutionResult {
        val evalLocation = steps.last()
        val evalFrame = imperativeModel.findLast(evalLocation)
        val evalState = evalFrame?.states?.get(evalLocation.objectPath)

        return evalState?.previous
                ?: ExecutionFailure("steps missing")
    }
}