package tech.kzen.auto.common.paradigm.imperative.service

import kotlinx.coroutines.delay
import tech.kzen.auto.common.objects.document.ScriptDocument
import tech.kzen.auto.common.paradigm.imperative.model.*
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
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
        suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation)
        suspend fun onExecutionModel(host: DocumentPath, executionModel: ExecutionModel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val subscribers = mutableSetOf<Observer>()
    private val models: MutableMap<DocumentPath, ExecutionModel> = mutableMapOf()


    private suspend fun modelOrInit(host: DocumentPath): ExecutionModel {
        val existing = models[host]
        if (existing != null) {
            return existing
        }
        val initial = executionInitializer.initialExecutionModel(host)
        models[host] = initial
        publishExecutionModel(host, initial)
        return initial
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun subscribe(subscriber: Observer) {
        subscribers.add(subscriber)

        for (model in models) {
//            println("^^^ subscribe - onExecutionModel - $model")
            subscriber.onExecutionModel(model.key, model.value)
        }
    }


    fun unsubscribe(subscriber: Observer) {
        subscribers.remove(subscriber)
    }


    private suspend fun publishBeforeExecution(
            host: DocumentPath,
            objectLocation: ObjectLocation
    ) {
        for (subscriber in subscribers) {
//            println("^^^ publishBeforeExecution - $host - $objectLocation")
            subscriber.beforeExecution(host, objectLocation)
        }
    }


    private suspend fun publishExecutionModel(
            host: DocumentPath,
            model: ExecutionModel
    ) {
        for (subscriber in subscribers) {
//            println("^^^ publishExecutionModel - $host - $model")
            subscriber.onExecutionModel(host, model)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(
            projectStructure: GraphStructure,
            event: NotationEvent?
    ) {
        if (event == null) {
            return
        }

        for (host in models.keys) {
            val model = modelOrInit(host)

            val changed = apply(model, event)

            if (changed) {
                publishExecutionModel(host, model)
            }
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
    suspend fun willExecute(
            host: DocumentPath,
            objectLocation: ObjectLocation
    ) {
        val model = modelOrInit(host)
        val existingFrame = model.findLast(objectLocation)

        val upsertFrame = existingFrame
                ?: model.frames.last()

        // TODO
        val state = upsertFrame.states[objectLocation.objectPath]
                ?: return

        upsertFrame.states[objectLocation.objectPath] = state.copy(running = true)

        publishExecutionModel(host, model)
    }


    suspend fun didExecute(
            host: DocumentPath,
            objectLocation: ObjectLocation,
            executionState: ExecutionState
    ) {
        val model = modelOrInit(host)
        val existingFrame = model.findLast(objectLocation)

        val upsertFrame = existingFrame
                ?: model.frames.last()

        upsertFrame.states[objectLocation.objectPath] = executionState

        publishExecutionModel(host, model)
    }



    //-----------------------------------------------------------------------------------------------------------------
    suspend fun isExecuting(
            host: DocumentPath
    ): Boolean {
        return modelOrInit(host).containsStatus(ExecutionPhase.Running)
    }


    suspend fun executionModel(
            host: DocumentPath
    ): ExecutionModel {
        return modelOrInit(host)
    }


    suspend fun readExecutionModel(
            host: DocumentPath,
            prototype: ExecutionModel
    ) {
        val model = modelOrInit(host)
        model.frames.clear()
        model.frames.addAll(prototype.frames)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun reset(
            host: DocumentPath
    ): Digest {
        val model = modelOrInit(host)

        model.frames.clear()

        publishExecutionModel(host, model)

        return model.digest()
    }


    suspend fun start(
            host: DocumentPath,
            graphStructure: GraphStructure
    ): Digest {
        val model = modelOrInit(host)
        model.frames.clear()

        val values = mutableMapOf<ObjectPath, ExecutionState>()

        val documentNotation = graphStructure.graphNotation.documents.values[host]

        if (documentNotation != null) {
            val steps = graphStructure.graphNotation.transitiveAttribute(
                    ObjectLocation(host, NotationConventions.mainObjectPath),
                    ScriptDocument.stepsAttributePath
            ) as ListAttributeNotation

            for (i in steps.values) {
                val objectPath = ObjectPath.parse(i.asString()!!)
                values[objectPath] = ExecutionState.initial
            }

            val frame = ExecutionFrame(host, values)

            model.frames.add(frame)
        }

        publishExecutionModel(host, model)

        return model.digest()
    }


    suspend fun execute(
            host: DocumentPath,
            objectLocation: ObjectLocation,
            delayMillis: Int = 0
    ): ExecutionResponse {
        if (delayMillis > 0) {
//            println("ExecutionManager | %%%% delay($delayMillis)")
            delay(delayMillis.toLong())
        }
        willExecute(host, objectLocation)

        publishBeforeExecution(host, objectLocation)

        if (delayMillis > 0) {
//            println("ExecutionManager | delay($delayMillis)")
            delay(delayMillis.toLong())
        }

        val response = actionExecutor.execute(objectLocation)

//        val parentRef = ObjectReference.parse(
//                modelManager.autoNotation().getString(objectLocation, NotationConventions.isAttribute))
//        val parentLocation = modelManager.autoNotation().coalesce.locate(objectLocation, parentRef)
//
//        val actionHandle = actionExecutor.actionManager().get(parentLocation)
//        val result = response.toResult(actionHandle)

        val digest = modelOrInit(host).digest()

        val executionState = ExecutionState(
                false,
                response
        )

        didExecute(host, objectLocation, executionState)

        return ExecutionResponse(response, digest)
    }
}
