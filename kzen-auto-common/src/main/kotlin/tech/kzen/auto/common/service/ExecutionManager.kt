package tech.kzen.auto.common.service

import kotlinx.coroutines.experimental.delay
import tech.kzen.auto.common.exec.ExecutionFrame
import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.exec.ExecutionStatus
import tech.kzen.lib.common.edit.ObjectRenamedEvent
import tech.kzen.lib.common.edit.ProjectEvent
import tech.kzen.lib.common.notation.model.ProjectPath


class ExecutionManager(
        private val actionExecutor: ActionExecutor
) : ModelManager.Subscriber {
    //-----------------------------------------------------------------------------------------------------------------
    interface Subscriber {
        suspend fun beforeExecution(executionModel: ExecutionModel)
        suspend fun afterExecution(executionModel: ExecutionModel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val subscribers = mutableSetOf<Subscriber>()
    private val model: ExecutionModel = ExecutionModel(mutableListOf())
//    private var projectModel: ProjectModel? = null


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun subscribe(subscriber: Subscriber) {
        subscribers.add(subscriber)
        subscriber.afterExecution(model)
    }


    fun unsubscribe(subscriber: Subscriber) {
        subscribers.remove(subscriber)
    }


    private suspend fun publishAfterExecution() {
        for (subscriber in subscribers) {
            subscriber.afterExecution(model)
        }
    }

    private suspend fun publishBeforeExecution() {
        for (subscriber in subscribers) {
            subscriber.beforeExecution(model)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(projectModel: ProjectModel, event: ProjectEvent?) {
        if (event == null) {
            return
        }

        val changed = when (event) {
            is ObjectRenamedEvent ->
                model.rename(event.objectName, event.newName)

            else ->
                false
        }

        if (changed) {
            publishAfterExecution()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun reset() {
        model.frames.clear()

        publishAfterExecution()
    }


    suspend fun start(main: ProjectPath, projectModel: ProjectModel) {
        model.frames.clear()

        val values = mutableMapOf<String, ExecutionStatus>()

        val packageNotation = projectModel.projectNotation.packages[main]

        if (packageNotation != null) {
            for (e in packageNotation.objects) {
                values[e.key] = ExecutionStatus.Pending
            }

            val frame = ExecutionFrame(main, values)

            model.frames.add(frame)
        }

        publishAfterExecution()
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun willExecute(objectName: String) {
        updateStatus(objectName, ExecutionStatus.Running)
    }


    suspend fun didExecute(objectName: String, success: Boolean) {
        val status =
                if (success) {
                    ExecutionStatus.Success
                }
                else {
                    ExecutionStatus.Failed
                }

        updateStatus(objectName, status)
    }


    private suspend fun updateStatus(objectName: String, status: ExecutionStatus) {
        val existingFrame = model.findLast(objectName)

        val upsertFrame = existingFrame
                ?: model.frames.last()

        upsertFrame.values[objectName] = status

        publishAfterExecution()
    }



    //-----------------------------------------------------------------------------------------------------------------
    fun isExecuting(): Boolean =
        model.containsStatus(ExecutionStatus.Running)


    suspend fun execute(
            objectName: String,
            delayMillis: Int = 0
    ): Boolean {
        if (delayMillis > 0) {
            println("ExecutionManager | %%%% delay($delayMillis)")
            delay(delayMillis)
        }
        willExecute(objectName)

        publishBeforeExecution()

        if (delayMillis > 0) {
            println("ExecutionManager | delay($delayMillis)")
            delay(delayMillis)
        }

        var success = false
        try {
            actionExecutor.execute(objectName)
            success = true
        }
        catch (e: Exception) {
            println("#$%#$%#$ got exception: $e")
        }

        didExecute(objectName, success)

        return success
    }
}
