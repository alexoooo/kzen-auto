package tech.kzen.auto.common.service

import kotlinx.coroutines.experimental.delay
import tech.kzen.auto.common.api.ActionExecution
import tech.kzen.auto.common.exec.ExecutionFrame
import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.exec.ExecutionStatus
import tech.kzen.lib.common.edit.ObjectRenamedEvent
import tech.kzen.lib.common.edit.ProjectEvent
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.util.Digest


class ExecutionManager(
        private val executionInitializer: ExecutionInitializer,
        private val actionExecutor: ActionExecutor
): ModelManager.Observer {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        suspend fun beforeExecution()
        suspend fun onExecutionModel(executionModel: ExecutionModel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val subscribers = mutableSetOf<Observer>()
    private var modelOrNull: ExecutionModel? = null


    // TODO: could be lazy?
    private suspend fun modelOrInit(): ExecutionModel {
        if (modelOrNull == null) {
            modelOrNull = executionInitializer.initialExecutionModel()
        }
        return modelOrNull!!
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun subscribe(subscriber: Observer) {
        subscribers.add(subscriber)
        subscriber.onExecutionModel(modelOrInit())
    }


    fun unsubscribe(subscriber: Observer) {
        subscribers.remove(subscriber)
    }


    private suspend fun publishBeforeExecution() {
        for (subscriber in subscribers) {
            subscriber.beforeExecution()
        }
    }


    private suspend fun publishExecutionModel(model: ExecutionModel) {
        for (subscriber in subscribers) {
            subscriber.onExecutionModel(model)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(autoModel: ProjectModel, event: ProjectEvent?) {
        if (event == null) {
            return
        }

        val model = modelOrInit()

        val changed = when (event) {
            is ObjectRenamedEvent ->
                model.rename(event.objectName, event.newName)

            else ->
                false
        }

        if (changed) {
            publishExecutionModel(model)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun willExecute(objectName: String) {
        updateStatus(objectName, ExecutionStatus.Running)
    }


    suspend fun didExecute(objectName: String, execution: ActionExecution) {
        updateStatus(objectName, execution.status)
    }


//    private suspend fun updateStatus(objectName: String, execution: ActionExecution) {
    private suspend fun updateStatus(objectName: String, status: ExecutionStatus) {
        val model = modelOrInit()
        val existingFrame = model.findLast(objectName)

        val upsertFrame = existingFrame
                ?: model.frames.last()

        upsertFrame.values[objectName] = status

        publishExecutionModel(model)
    }



    //-----------------------------------------------------------------------------------------------------------------
    suspend fun isExecuting(): Boolean {
        return modelOrInit().containsStatus(ExecutionStatus.Running)
    }


    suspend fun executionModel(): ExecutionModel {
        return modelOrInit()
    }


    suspend fun readExecutionModel(prototype: ExecutionModel) {
        val model = modelOrInit()
        model.frames.clear()
        model.frames.addAll(prototype.frames)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun reset(): Digest {
        val model = modelOrInit()

        model.frames.clear()

        publishExecutionModel(model)

        return model.digest()
    }


    suspend fun start(main: ProjectPath, projectModel: ProjectModel): Digest {
        val model = modelOrInit()
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

        publishExecutionModel(model)

        return model.digest()
    }


    suspend fun execute(
            objectName: String,
            delayMillis: Int = 0
    ): ActionExecution {
        if (delayMillis > 0) {
//            println("ExecutionManager | %%%% delay($delayMillis)")
            delay(delayMillis.toLong())
        }
        willExecute(objectName)

        publishBeforeExecution()

        if (delayMillis > 0) {
//            println("ExecutionManager | delay($delayMillis)")
            delay(delayMillis.toLong())
        }

        var success = false
        try {
            actionExecutor.execute(objectName)
            success = true
        }
        catch (e: Exception) {
            println("#$%#$%#$ got exception: $e")
        }

        val status =
                if (success) {
                    ExecutionStatus.Success
                }
                else {
                    ExecutionStatus.Failed
                }

        val digest = modelOrInit().digest()

        val execution = ActionExecution(
                status, digest)

        didExecute(objectName, execution)

        return execution
    }
}
