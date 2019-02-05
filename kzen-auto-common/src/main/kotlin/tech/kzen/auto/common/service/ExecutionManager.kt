package tech.kzen.auto.common.service

import kotlinx.coroutines.delay
import tech.kzen.auto.common.api.ActionExecution
import tech.kzen.auto.common.exec.ExecutionFrame
import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.exec.ExecutionStatus
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectPath
import tech.kzen.lib.common.notation.edit.*
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
    override suspend fun handleModel(autoModel: ProjectModel, event: NotationEvent?) {
        if (event == null) {
            return
        }

        val model = modelOrInit()

        val changed = apply(model, event)

        if (changed) {
            publishExecutionModel(model)
        }
    }


    private fun apply(model: ExecutionModel, event: NotationEvent): Boolean {
        return when (event) {
            is SingularNotationEvent ->
                applySingular(model, event)

            is CompoundNotationEvent -> {
                var anyChanged = false
                for (singularEvent in event.singularEvents) {
                    anyChanged = anyChanged || applySingular(model, singularEvent)
                }
                return anyChanged
            }
        }
    }


    private fun applySingular(model: ExecutionModel, event: SingularNotationEvent): Boolean {
        return when (event) {
            is RenamedObjectEvent ->
                model.rename(event.objectLocation, event.newName)

            is AddedObjectEvent ->
                model.add(event.objectLocation)

            else ->
                false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun willExecute(objectLocation: ObjectLocation) {
        updateStatus(objectLocation, ExecutionStatus.Running)
    }


    suspend fun didExecute(objectLocation: ObjectLocation, execution: ActionExecution) {
        updateStatus(objectLocation, execution.status)
    }


    private suspend fun updateStatus(objectLocation: ObjectLocation, status: ExecutionStatus) {
        val model = modelOrInit()
        val existingFrame = model.findLast(objectLocation)

        val upsertFrame = existingFrame
                ?: model.frames.last()

        upsertFrame.values[objectLocation.objectPath] = status

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


    suspend fun start(main: BundlePath, projectModel: ProjectModel): Digest {
        val model = modelOrInit()
        model.frames.clear()

        val values = mutableMapOf<ObjectPath, ExecutionStatus>()

        val packageNotation = projectModel.projectNotation.bundles.values[main]

        if (packageNotation != null) {
            for (e in packageNotation.objects.values) {
                values[e.key] = ExecutionStatus.Pending
            }

            val frame = ExecutionFrame(main, values)

            model.frames.add(frame)
        }

        publishExecutionModel(model)

        return model.digest()
    }


    suspend fun execute(
            objectLocation: ObjectLocation,
            delayMillis: Int = 0
    ): ActionExecution {
        if (delayMillis > 0) {
//            println("ExecutionManager | %%%% delay($delayMillis)")
            delay(delayMillis.toLong())
        }
        willExecute(objectLocation)

        publishBeforeExecution()

        if (delayMillis > 0) {
//            println("ExecutionManager | delay($delayMillis)")
            delay(delayMillis.toLong())
        }

        var success = false
        try {
            actionExecutor.execute(objectLocation)
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

        didExecute(objectLocation, execution)

        return execution
    }
}
