package tech.kzen.auto.client.service.global

import tech.kzen.lib.common.model.locate.ObjectLocation


class InsertionGlobal {
    //-----------------------------------------------------------------------------------------------------------------
    interface Subscriber {
        fun onInsertionSelected(action: ObjectLocation)
        fun onInsertionUnselected()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val subscribers = mutableListOf<Subscriber>()

    private var selected: ObjectLocation? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun subscribe(subscriber: Subscriber) {
        subscribers.add(subscriber)
    }


    fun unsubscribe(subscriber: Subscriber) {
        subscribers.remove(subscriber)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun setSelected(action: ObjectLocation) {
        selected = action

        for (observer in subscribers) {
            observer.onInsertionSelected(action)
        }
    }


    fun clearSelection() {
        selected = null

        for (observer in subscribers) {
            observer.onInsertionUnselected()
        }
    }


    fun getAndClearSelection(): ObjectLocation? {
        if (selected == null) {
            return null
        }

        val selectedAction = selected!!

        clearSelection()

        return selectedAction
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isSelected(): Boolean {
        return selected != null
    }
}