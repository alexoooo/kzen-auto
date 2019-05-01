package tech.kzen.auto.common.paradigm.imperative.service

import kotlinx.coroutines.delay
import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.auto.common.paradigm.imperative.model.*
import tech.kzen.auto.common.service.GraphStructureManager
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
):
        GraphStructureManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation)
        suspend fun onExecutionModel(host: DocumentPath, executionModel: ImperativeModel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var models: PersistentMap<DocumentPath, ImperativeModel> = persistentMapOf()
//    private var version = 0


    private suspend fun modelOrInit(host: DocumentPath): ImperativeModel {
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
    suspend fun observe(observer: Observer) {
        observers.add(observer)

//        println("!!! observe - onExecutionModel - $models")
        for (model in models) {
            observer.onExecutionModel(model.key, model.value)
        }
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    private suspend fun publishBeforeExecution(
            host: DocumentPath,
            objectLocation: ObjectLocation
    ) {
//        println("^^^ publishBeforeExecution - $host - $objectLocation")
        for (subscriber in observers) {
            subscriber.beforeExecution(host, objectLocation)
        }
    }


    private suspend fun publishExecutionModel(
            host: DocumentPath,
            model: ImperativeModel
    ) {
//        val model = models[host]!!

//        val current = version++
//        println("^^^ publishExecutionModel - $current - $host - $model")
        for (subscriber in observers) {
//            println("^^^ ### publishExecutionModel - to subscriber - $current")
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

//            val model = models[host]!!
            val newModels = apply(host, /*model,*/ event)

            if (models != newModels) {
                models = newModels
//                publishExecutionModel(host, models[host]!!)
                if (host in models) {
                    publishExecutionModel(host, models[host]!!)
                }
            }
        }
    }


    private fun apply(
            documentPath: DocumentPath,
            event: NotationEvent
    ): PersistentMap<DocumentPath, ImperativeModel> {
        return when (event) {
            is SingularNotationEvent ->
                applySingular(documentPath, models, event)

            is CompoundNotationEvent -> {
                applyCompound(documentPath, event)
            }
        }
    }


    private fun applyCompound(
            documentPath: DocumentPath,
            event: CompoundNotationEvent
    ): PersistentMap<DocumentPath, ImperativeModel> {
        val model = models[documentPath]!!
        val appliedWithDependentEvents = applyCompoundWithDependentEvents(
                documentPath, model, event)
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
            model: ImperativeModel,
            event: CompoundNotationEvent
    ): PersistentMap<DocumentPath, ImperativeModel> {
        return when (event) {
            is RenamedDocumentRefactorEvent -> {
//                println("^^^^^ applyCompoundWithDependentEvents - $documentPath - $event")
                if (event.removedUnderOldName.documentPath == documentPath) {
                    val newModel = model.move(
                            event.removedUnderOldName.documentPath, event.createdWithNewName.destination)

                    val removed = models.remove(event.removedUnderOldName.documentPath)
                    removed.put(event.createdWithNewName.destination, newModel)
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
            currentModels: PersistentMap<DocumentPath, ImperativeModel>,
            event: SingularNotationEvent
    ): PersistentMap<DocumentPath, ImperativeModel> {
        val model = currentModels[documentPath]!!

        return when (event) {
            is RemovedObjectEvent ->
                currentModels.put(documentPath,
                        model.remove(event.objectLocation))

            is RenamedObjectEvent ->
                currentModels.put(documentPath,
                        model.rename(event.objectLocation, event.newName))

            is AddedObjectEvent ->
                currentModels.put(documentPath,
                        model.add(event.objectLocation))

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
    suspend fun isExecuting(
            host: DocumentPath
    ): Boolean {
        return modelOrInit(host).containsStatus(ImperativePhase.Running)
    }


    suspend fun executionModel(
            host: DocumentPath
    ): ImperativeModel {
        return modelOrInit(host)
    }


//    fun readExecutionModel(
//            host: DocumentPath,
//            prototype: ExecutionModel
//    ) {
//        models = models.put(host, prototype)
//
////        val model = modelOrInit(host)
////        model.frames.clear()
////        model.frames.addAll(prototype.frames)
//    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun reset(
            host: DocumentPath
    ): Digest {
        val model = ImperativeModel(persistentListOf())
        models = models.put(host, model)

        publishExecutionModel(host, model)

        return model.digest()
    }


    suspend fun start(
            host: DocumentPath,
            graphStructure: GraphStructure
    ): Digest {
        val documentNotation = graphStructure.graphNotation.documents.values[host]

        if (documentNotation != null) {
            val steps = graphStructure.graphNotation.transitiveAttribute(
                    ObjectLocation(host, NotationConventions.mainObjectPath),
                    ScriptDocument.stepsAttributePath
            ) as ListAttributeNotation

            val values = mutableMapOf<ObjectPath, ImperativeState>()
            for (i in steps.values) {
                val objectPath = ObjectPath.parse(i.asString()!!)
                values[objectPath] = ImperativeState.initial
            }

            val frame = ImperativeFrame(host, values.toPersistentMap())
            val executionModel = ImperativeModel(persistentListOf(frame))

            models = models.put(host, executionModel)
//            model.frames.add(frame)
        }

        val model = modelOrInit(host)
        publishExecutionModel(host, model)
        return model.digest()
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun execute(
            host: DocumentPath,
            objectLocation: ObjectLocation,
            delayMillis: Int = 0
    ): ImperativeResponse {
        if (delayMillis > 0) {
//            println("ExecutionManager | %%%% delay($delayMillis)")
            delay(delayMillis.toLong())
        }
        willExecute(host, objectLocation)

        if (delayMillis > 0) {
//            println("ExecutionManager | delay($delayMillis)")
            delay(delayMillis.toLong())
        }

        val response = actionExecutor.execute(objectLocation)

        val digest = modelOrInit(host).digest()

        val executionState = ImperativeState(
                false,
                response
        )

        didExecute(host, objectLocation, executionState)

        return ImperativeResponse(response, digest)
    }


    private suspend fun willExecute(
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

        val updatedModel = ImperativeModel(
                model.frames.set(model.frames.size - 1, updatedFrame))

        models = models.put(host, updatedModel)
//        upsertFrame.states[objectLocation.objectPath] = state.copy(running = true)

        publishExecutionModel(host, updatedModel)
        publishBeforeExecution(host, objectLocation)
    }


    private suspend fun didExecute(
            host: DocumentPath,
            objectLocation: ObjectLocation,
            executionState: ImperativeState
    ) {
        val model = modelOrInit(host)
        val existingFrame = model.findLast(objectLocation)

        val upsertFrame = existingFrame
                ?: model.frames.last()

        val updatedFrame = upsertFrame.set(
                objectLocation.objectPath,
                executionState)
//        upsertFrame.states[objectLocation.objectPath] = executionState

        val updatedModel = ImperativeModel(
                model.frames.set(model.frames.size - 1, updatedFrame))

        models = models.put(host, updatedModel)

//        println("^^^ didExecute: $host - $updatedModel")
        publishExecutionModel(host, updatedModel)
    }
}
