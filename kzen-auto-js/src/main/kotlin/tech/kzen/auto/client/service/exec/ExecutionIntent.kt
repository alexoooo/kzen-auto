package tech.kzen.auto.client.service.exec

import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.service.ExecutionManager
import tech.kzen.lib.common.api.model.ObjectLocation


class ExecutionIntent: ExecutionManager.Observer {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onExecutionIntent(actionLocation: ObjectLocation?)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var actionLocation: ObjectLocation? = null


    fun observe(observer: Observer) {
        observers.add(observer)

        if (actionLocation != null) {
            observer.onExecutionIntent(actionLocation)
        }
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    private fun publish() {
        for (observer in observers) {
            observer.onExecutionIntent(actionLocation)
        }
    }

    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeExecution(objectLocation: ObjectLocation) {
        set(objectLocation)
    }

    override suspend fun onExecutionModel(executionModel: ExecutionModel) {}


    //-----------------------------------------------------------------------------------------------------------------
    fun clear() {
        if (actionLocation != null) {
            actionLocation = null
            publish()
        }
    }


    fun clearIf(actionLocation: ObjectLocation) {
        if (this.actionLocation == actionLocation) {
            clear()
        }
    }

    fun set(actionLocation: ObjectLocation) {
        if (this.actionLocation != actionLocation) {
            this.actionLocation = actionLocation
            publish()
        }
    }
}