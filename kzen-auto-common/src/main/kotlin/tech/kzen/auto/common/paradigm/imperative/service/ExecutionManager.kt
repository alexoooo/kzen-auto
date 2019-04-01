package tech.kzen.auto.common.paradigm.imperative.service

import kotlinx.coroutines.delay
import tech.kzen.auto.common.objects.document.ScriptDocument
import tech.kzen.auto.common.paradigm.imperative.model.*
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.api.model.DocumentPath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectPath
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.*
import tech.kzen.lib.common.structure.notation.model.ListAttributeNotation
import tech.kzen.lib.common.util.Digest


class ExecutionManager(
        private val executionInitializer: ExecutionInitializer,
        private val actionExecutor: ActionExecutor
): ModelManager.Observer {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        suspend fun beforeExecution(objectLocation: ObjectLocation)
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


    private suspend fun publishBeforeExecution(objectLocation: ObjectLocation) {
        for (subscriber in subscribers) {
            subscriber.beforeExecution(objectLocation)
        }
    }


    private suspend fun publishExecutionModel(model: ExecutionModel) {
        for (subscriber in subscribers) {
            subscriber.onExecutionModel(model)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(projectStructure: GraphStructure, event: NotationEvent?) {
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
            is RemovedObjectEvent ->
                model.remove(event.objectLocation)

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
        val model = modelOrInit()
        val existingFrame = model.findLast(objectLocation)

        val upsertFrame = existingFrame
                ?: model.frames.last()

        // TODO
        val state = upsertFrame.states[objectLocation.objectPath]
                ?: return

        upsertFrame.states[objectLocation.objectPath] = state.copy(running = true)

        publishExecutionModel(model)
    }


    suspend fun didExecute(objectLocation: ObjectLocation, executionState: ExecutionState) {
        val model = modelOrInit()
        val existingFrame = model.findLast(objectLocation)

        val upsertFrame = existingFrame
                ?: model.frames.last()

        upsertFrame.states[objectLocation.objectPath] = executionState

        publishExecutionModel(model)
    }



    //-----------------------------------------------------------------------------------------------------------------
    suspend fun isExecuting(): Boolean {
        return modelOrInit().containsStatus(ExecutionPhase.Running)
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


    suspend fun start(
            documentPath: DocumentPath,
            graphStructure: GraphStructure
    ): Digest {
        val model = modelOrInit()
        model.frames.clear()

        val values = mutableMapOf<ObjectPath, ExecutionState>()

        val documentNotation = graphStructure.graphNotation.documents.values[documentPath]

        if (documentNotation != null) {
            val steps = graphStructure.graphNotation.transitiveAttribute(
                    ObjectLocation(documentPath, NotationConventions.mainObjectPath),
                    ScriptDocument.stepsAttributePath
            ) as ListAttributeNotation

            for (i in steps.values) {
                val objectPath = ObjectPath.parse(i.asString()!!)
                values[objectPath] = ExecutionState.initial
            }

            val frame = ExecutionFrame(documentPath, values)

            model.frames.add(frame)
        }

        publishExecutionModel(model)

        return model.digest()
    }


    suspend fun execute(
            objectLocation: ObjectLocation,
            delayMillis: Int = 0
    ): ExecutionResponse {
        if (delayMillis > 0) {
//            println("ExecutionManager | %%%% delay($delayMillis)")
            delay(delayMillis.toLong())
        }
        willExecute(objectLocation)

        publishBeforeExecution(objectLocation)

        if (delayMillis > 0) {
//            println("ExecutionManager | delay($delayMillis)")
            delay(delayMillis.toLong())
        }

        val response: ExecutionResult = try {
            actionExecutor.execute(objectLocation)
        }
        catch (e: Exception) {
            println("#$%#$%#$ got exception: $e")

//            val digest = modelOrInit().digest()
//            ExecutionResponse(
//                    ,
//                    digest
//            )
            ExecutionError(e.message ?: "Error")
        }

//        val parentRef = ObjectReference.parse(
//                modelManager.autoNotation().getString(objectLocation, NotationConventions.isAttribute))
//        val parentLocation = modelManager.autoNotation().coalesce.locate(objectLocation, parentRef)
//
//        val actionHandle = actionExecutor.actionManager().get(parentLocation)
//        val result = response.toResult(actionHandle)

        val digest = modelOrInit().digest()

        val executionState = ExecutionState(
                false,
                response
        )

        didExecute(objectLocation, executionState)

        return ExecutionResponse(response, digest)
    }
}
