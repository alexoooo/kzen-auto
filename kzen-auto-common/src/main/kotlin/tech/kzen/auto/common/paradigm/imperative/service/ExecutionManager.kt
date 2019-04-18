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
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.persistentListOf
import tech.kzen.lib.platform.collect.persistentMapOf
import tech.kzen.lib.platform.collect.toPersistentMap


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
    private var models: PersistentMap<DocumentPath, ExecutionModel> = persistentMapOf()


    private suspend fun modelOrInit(host: DocumentPath): ExecutionModel {
        val existing = models[host]
        if (existing != null) {
            return existing
        }
        val initial = executionInitializer.initialExecutionModel(host)
        models = models.put(host, initial)
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
//        val model = models[host]!!

        for (subscriber in subscribers) {
//            println("^^^ publishExecutionModel - $host - $model")
            subscriber.onExecutionModel(host, model)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(
            graphStructure: GraphStructure,
            event: NotationEvent?
    ) {
        if (event == null) {
            return
        }

        // NB: avoid concurrent modification for DeletedDocumentEvent handling
//        val modelHosts = models.keys.toList()

        for (host in models.keys) {
//            val model = modelOrInit(host)

            val model = models[host]!!
            val newModels = apply(host, /*model,*/ event)

            if (models != newModels) {
                models = newModels
                publishExecutionModel(host, models[host]!!)
            }
        }
    }


    private fun apply(
            documentPath: DocumentPath,
//            currentModels: PersistentMap<DocumentPath, ExecutionModel>,
//            model: ExecutionModel,
            event: NotationEvent
    ): PersistentMap<DocumentPath, ExecutionModel> {
//        val model = models[documentPath]!!
        return when (event) {
            is SingularNotationEvent ->
                applySingular(documentPath, models, /*model,*/ event)

            is CompoundNotationEvent -> {
                applyCompound(documentPath, /*currentModels, model,*/ event)
            }
        }
    }


    private fun applyCompound(
            documentPath: DocumentPath,
//            currentModels: PersistentMap<DocumentPath, ExecutionModel>,
//            model: ExecutionModel,
            event: CompoundNotationEvent
    ): PersistentMap<DocumentPath, ExecutionModel> {
        val model = models[documentPath]!!
        val appliedWithDependentEvents = applyCompoundWithDependentEvents(
                documentPath, /*currentModels,*/ model, event)
        if (models != appliedWithDependentEvents) {
            return appliedWithDependentEvents
        }

        var builder = models
        for (singularEvent in event.singularEvents) {
            builder = applySingular(documentPath, builder, singularEvent)
        }
        return builder
    }


    private fun applyCompoundWithDependentEvents(
            documentPath: DocumentPath,
//            currentModels: PersistentMap<DocumentPath, ExecutionModel>,
            model: ExecutionModel,
            event: CompoundNotationEvent
    ): PersistentMap<DocumentPath, ExecutionModel> {
        return when (event) {
            is RenamedDocumentRefactorEvent -> {
//                println("^^^^^ applyCompoundWithDependentEvents - $documentPath - $event")
                if (event.removedUnderOldName.documentPath == documentPath) {
                    val removed = models.remove(event.removedUnderOldName.documentPath)
                    removed.put(event.createdWithNewName.destination, model)
                }
                else {
                    models
                }
            }

            else ->
                models
        }
    }


    private fun applySingular(
            documentPath: DocumentPath,
            currentModels: PersistentMap<DocumentPath, ExecutionModel>,
//            model: ExecutionModel,
            event: SingularNotationEvent
    ): PersistentMap<DocumentPath, ExecutionModel> {
        val model = models[documentPath]!!

        return when (event) {
            is RemovedObjectEvent ->
                currentModels.put(documentPath,
                        model.remove(event.objectLocation))

            is RenamedObjectEvent ->
                currentModels.put(documentPath,
                        model.rename(event.objectLocation, event.newName))

            is AddedObjectEvent ->
                currentModels.put(documentPath,
                        model.add(event.objectLocation/*, event.indexInDocument.value - 1*/))

            is DeletedDocumentEvent ->
                if (event.documentPath == documentPath) {
                    currentModels.remove(documentPath)
                }
                else {
                    currentModels
                }

            else ->
                currentModels
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

        val state = upsertFrame.states[objectLocation.objectPath]
                ?: return

        val updatedFrame = upsertFrame.set(
                objectLocation.objectPath,
                state.copy(running = true))

        val updatedModel = ExecutionModel(
                model.frames.set(model.frames.size - 1, updatedFrame))

        models = models.put(host, updatedModel)
//        upsertFrame.states[objectLocation.objectPath] = state.copy(running = true)

        publishExecutionModel(host, updatedModel)
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

        val updatedFrame = upsertFrame.set(
                objectLocation.objectPath,
                executionState)
//        upsertFrame.states[objectLocation.objectPath] = executionState

        val updatedModel = ExecutionModel(
                model.frames.set(model.frames.size - 1, updatedFrame))

        models = models.put(host, updatedModel)

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
        models = models.put(host, prototype)

//        val model = modelOrInit(host)
//        model.frames.clear()
//        model.frames.addAll(prototype.frames)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun reset(
            host: DocumentPath
    ): Digest {
//        val model = modelOrInit(host)

        val model = ExecutionModel(persistentListOf())
        models = models.put(host, model)
//        model.frames.clear()

        publishExecutionModel(host, model)

        return model.digest()
    }


    suspend fun start(
            host: DocumentPath,
            graphStructure: GraphStructure
    ): Digest {
//        val model = modelOrInit(host)
//        model.frames.clear()

        val documentNotation = graphStructure.graphNotation.documents.values[host]

        if (documentNotation != null) {
            val steps = graphStructure.graphNotation.transitiveAttribute(
                    ObjectLocation(host, NotationConventions.mainObjectPath),
                    ScriptDocument.stepsAttributePath
            ) as ListAttributeNotation

            val values = mutableMapOf<ObjectPath, ExecutionState>()
            for (i in steps.values) {
                val objectPath = ObjectPath.parse(i.asString()!!)
                values[objectPath] = ExecutionState.initial
            }

            val frame = ExecutionFrame(host, values.toPersistentMap())
            val executionModel = ExecutionModel(persistentListOf(frame))

            models = models.put(host, executionModel)
//            model.frames.add(frame)
        }

        val model = modelOrInit(host)
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
