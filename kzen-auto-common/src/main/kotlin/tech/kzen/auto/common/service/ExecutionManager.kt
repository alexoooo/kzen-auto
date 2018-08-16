package tech.kzen.auto.common.service

import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.getAnswerFoo
import tech.kzen.lib.common.edit.ObjectRenamedEvent
import tech.kzen.lib.common.edit.ProjectEvent


class ExecutionManager {
    //-----------------------------------------------------------------------------------------------------------------
    interface Subscriber {
        fun handle(executionModel: ExecutionModel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val subscribers = mutableSetOf<Subscriber>()
    private var current: ExecutionModel = ExecutionModel(mutableListOf())


    //-----------------------------------------------------------------------------------------------------------------
    fun subscribe(subscriber: Subscriber) {
        subscribers.add(subscriber)
        subscriber.handle(current)
    }


    fun unsubscribe(subscriber: Subscriber) {
        subscribers.remove(subscriber)
    }

    private fun publish() {
        for (subscriber in subscribers) {
            subscriber.handle(current)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun onEvent(event: ProjectEvent) {
        val next = when (event) {
            is ObjectRenamedEvent ->
                current.rename(event.objectName, event.newName)

            else ->
                current

        }

        if (next != next) {
            current = next
            publish()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun onExecution(objectName: String, success: Boolean) {

    }
}
