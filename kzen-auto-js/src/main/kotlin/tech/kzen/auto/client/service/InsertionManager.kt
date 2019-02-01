package tech.kzen.auto.client.service

import tech.kzen.auto.client.objects.action.NameConventions
import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.notation.edit.AddObjectCommand
import tech.kzen.lib.common.notation.model.PositionIndex


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


    suspend fun create(path: BundlePath, index: Int) {
        if (selected == null) {
            return
        }

        val selectedAction = selected!!

        clearSelection()

        val newObjectLocation = ObjectLocation(
                path,
                ObjectPath(NameConventions.randomAnonymous(), BundleNesting.root))

        val command = AddObjectCommand.ofParent(
                newObjectLocation,
                PositionIndex(index),
                selectedAction.toReference())

//        console.log("Creating: $command", command)

        ClientContext.commandBus.apply(command)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isSelected(): Boolean {
        return selected != null
    }
}