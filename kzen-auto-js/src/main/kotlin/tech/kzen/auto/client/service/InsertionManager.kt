package tech.kzen.auto.client.service

import tech.kzen.auto.client.objects.document.script.ScriptController
import tech.kzen.auto.client.util.NameConventions
import tech.kzen.lib.common.api.model.DocumentPath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.structure.notation.model.ObjectNotation
import tech.kzen.lib.common.structure.notation.model.PositionIndex


class InsertionManager {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onSelected(action: ObjectLocation)
        fun onUnselected()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val subscribers = mutableListOf<Observer>()

    private var selected: ObjectLocation? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun subscribe(observer: Observer) {
        subscribers.add(observer)
    }


    fun unSubscribe(observer: Observer) {
        subscribers.remove(observer)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun setSelected(action: ObjectLocation) {
        selected = action

        for (observer in subscribers) {
            observer.onSelected(action)
        }
    }


    fun clearSelection() {
        selected = null

        for (observer in subscribers) {
            observer.onUnselected()
        }
    }


    suspend fun create(path: DocumentPath, index: Int) {
        if (selected == null) {
            return
        }

        val selectedAction = selected!!

        clearSelection()

//        val newObjectLocation = ObjectLocation(
//                path,
//                ObjectPath(NameConventions.randomAnonymous(), DocumentNesting.root))
//
//        val command = AddObjectCommand.ofParent(
//                newObjectLocation,
//                PositionIndex(index),
//                selectedAction.toReference().name)

        val containingObjectLocation = ObjectLocation(
                path, NotationConventions.mainObjectPath)

        // NB: +1 offset for main Script object
        val positionInDocument = PositionIndex(index + 1)

        val command = InsertObjectInListAttributeCommand(
                containingObjectLocation,
                ScriptController.stepsAttributePath,
                PositionIndex(index),
                NameConventions.randomAnonymous(),
                positionInDocument,
                ObjectNotation.ofParent(selectedAction.toReference().name)
        )

//        console.log("Creating: $command", command)

        ClientContext.commandBus.apply(command)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isSelected(): Boolean {
        return selected != null
    }
}