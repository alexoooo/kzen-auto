package tech.kzen.auto.client.service

import tech.kzen.auto.client.objects.action.NameConventions
import tech.kzen.lib.common.edit.AddObjectCommand
import tech.kzen.lib.common.notation.model.ProjectPath


class InsertionManager(
//        private val commandBus: CommandBus
) {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onSelected(actionName: String)
        fun onUnselected()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val subscribers = mutableListOf<Observer>()

    private var selected: String? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun subscribe(observer: Observer) {
        subscribers.add(observer)
    }


    fun unSubscribe(observer: Observer) {
        subscribers.remove(observer)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun setSelected(actionName: String) {
        selected = actionName

        for (observer in subscribers) {
            observer.onSelected(actionName)
        }
    }


    fun clearSelection() {
        selected = null

        for (observer in subscribers) {
            observer.onUnselected()
        }
    }


    suspend fun create(path: ProjectPath, index: Int) {
        if (selected != null) {
            val selectedName = selected!!

            clearSelection()

            ClientContext.commandBus.apply(AddObjectCommand.ofParent(
                    path,
                    NameConventions.randomDefault(),
                    selectedName,
                    index))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isSelected(): Boolean {
        return selected != null
    }
}