package tech.kzen.auto.client.service

import tech.kzen.lib.common.model.locate.ObjectLocation


class InsertionGlobal {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onInsertionSelected(action: ObjectLocation)
        fun onInsertionUnselected()
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