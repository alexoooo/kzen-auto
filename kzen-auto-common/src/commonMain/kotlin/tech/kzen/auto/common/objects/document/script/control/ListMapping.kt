package tech.kzen.auto.common.objects.document.script.control

import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.imperative.api.ScriptControl
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.control.*
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class ListMapping(
        private val selfLocation: ObjectLocation,
        private val items: ObjectLocation,
        private val steps: List<ObjectLocation>
): ScriptControl {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val itemsAttributeName = AttributeName("items")

        const val indexKey = "index"
        const val bufferKey = "buffer"
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
                    EvaluateControlTransition(
                            listValue)
                }
                else {
                    InternalControlTransition(0, MapExecutionValue(mapOf(
                            indexKey to NumberExecutionValue(0.0),
                            bufferKey to ListExecutionValue(listOf()))))
                }
            }

            is InternalControlState -> {
                val values = (controlState.value as MapExecutionValue).values

                val index = (values[indexKey] as NumberExecutionValue).value.toInt()
                val buffer = (values[bufferKey] as ListExecutionValue).values

                val nextIndex = (index + 1).toDouble()

                val evalLocation = steps.last()
                val evalFrame = imperativeModel.findLast(evalLocation)
                val evalState = evalFrame?.states?.get(evalLocation.objectPath)
                val branchValue = (evalState?.previous as ExecutionSuccess).value

                val nextBuffer = buffer + branchValue

                if (nextIndex >= listValue.values.size) {
                    EvaluateControlTransition(ListExecutionValue(nextBuffer))
                }
                else {
                    InternalControlTransition(0, MapExecutionValue(mapOf(
                            indexKey to NumberExecutionValue(nextIndex),
                            bufferKey to ListExecutionValue(nextBuffer))))
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
        val selfFrame = imperativeModel.findLast(selfLocation)!!
        val selfState = selfFrame.states[selfLocation.objectPath]!!
        val selfControl = selfState.controlState!! as FinalControlState

        return ExecutionSuccess(
                selfControl.value,
                NullExecutionValue)
    }
}