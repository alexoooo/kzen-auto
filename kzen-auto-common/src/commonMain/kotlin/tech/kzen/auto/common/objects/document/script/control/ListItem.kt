package tech.kzen.auto.common.objects.document.script.control

import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.imperative.api.ScriptStep
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.control.InternalControlState
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class ListItem(
        private val selfLocation: ObjectLocation
): ScriptStep {
    override suspend fun perform(
            imperativeModel: ImperativeModel,
            graphInstance: GraphInstance
    ): ExecutionResult {
        val parentLocation = selfLocation.parent()
                ?: return ExecutionFailure("Parent location not found")

        val parentFrame = imperativeModel.findLast(parentLocation)
                ?: return ExecutionFailure("Parent frame not found")

        val parentState = parentFrame.states[parentLocation.objectPath]
                ?: return ExecutionFailure("Parent state not found")

        val parentControl = parentState.controlState
                ?: return ExecutionFailure("Parent control not found")

        val parentInternal =  parentControl as? InternalControlState
                ?: return ExecutionFailure("Parent internals not found")

        val parentValue = parentInternal.value as MapExecutionValue

        val listIndex = (parentValue.values[ListMapping.indexKey] as NumberExecutionValue).value.toInt()

        val listLocation = graphInstance[parentLocation]!!
                .constructorAttributes[ListMapping.itemsAttributeName]
                as? ObjectLocation
                ?: return ExecutionFailure("List location not found")

        val listFrame = imperativeModel.findLast(listLocation)
                ?: return ExecutionFailure("List frame not found")

        val listState = listFrame.states[listLocation.objectPath]
                ?: return ExecutionFailure("List state not found")

        val listResult = listState.previous
                ?: return ExecutionFailure("List result not found")

        val listSuccess = listResult as? ExecutionSuccess
                ?: return ExecutionFailure("List failed")

        val listValue = listSuccess.value as? ListExecutionValue
                ?: return ExecutionFailure("Not a list: ${listLocation.objectPath.name}")

        if (listValue.values.size <= listIndex) {
            return ExecutionFailure("Not item out of bounds: " +
                    "${listIndex + 1} of ${listValue.values.size} - ${listLocation.objectPath.name}")
        }

        val listItem = listValue.values[listIndex]

        return ExecutionSuccess.ofValue(listItem)
    }
}