package tech.kzen.auto.common.paradigm.dataflow.service.visual

import kotlinx.coroutines.delay
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.persistentMapOf
import kotlin.time.Duration.Companion.milliseconds


class VisualDataflowRepository(
    private val visualDataflowProvider: VisualDataflowProvider
):
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        suspend fun beforeDataflowExecution(host: DocumentPath, vertexLocation: ObjectLocation)
        suspend fun onVisualDataflowModel(host: DocumentPath, visualDataflowModel: VisualDataflowModel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var models: PersistentMap<DocumentPath, VisualDataflowModel> = persistentMapOf()


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun observe(observer: Observer) {
        observers.add(observer)

        for ((host, model) in models) {
            observer.onVisualDataflowModel(host, model)
        }
    }


    fun unobserve(observer: Observer) {
        observers.removeAll { it == observer }
    }


    private suspend fun publishBeforeExecution(
        host: DocumentPath,
        vertexLocation: ObjectLocation
    ) {
        for (observer in observers) {
            observer.beforeDataflowExecution(host, vertexLocation)
        }
    }


    private suspend fun publishModel(
        host: DocumentPath,
        model: VisualDataflowModel
    ) {
        for (observer in observers) {
            observer.onVisualDataflowModel(host, model)
        }
    }


    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        for (host in models.keys) {
            val newModels = apply(host, event)

            if (models != newModels) {
                models = newModels
                if (host in models) {
                    publishModel(host, models[host]!!)
                }
            }
        }
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}


    private fun apply(
        documentPath: DocumentPath,
        event: NotationEvent
    ): PersistentMap<DocumentPath, VisualDataflowModel> {
        return when (event) {
            is SingularNotationEvent ->
                applySingular(documentPath, models, event)

            is CompoundNotationEvent -> {
                applyCompound(documentPath, event)
            }
        }
    }


    private fun applySingular(
        documentPath: DocumentPath,
        currentModels: PersistentMap<DocumentPath, VisualDataflowModel>,
        event: SingularNotationEvent
    ): PersistentMap<DocumentPath, VisualDataflowModel> {
        if (documentPath != event.documentPath) {
            return currentModels
        }

        val model = currentModels[documentPath]!!

        return when (event) {
            is RemovedObjectEvent ->
                currentModels.put(documentPath,
                        model.remove(event.objectLocation))

            is RenamedObjectEvent ->
                currentModels.put(documentPath,
                        model.rename(event.objectLocation, event.newName))

            is AddedObjectEvent ->
                // Construct locally; calling provider.inspectVertex would race the parallel POST in MirroredGraphStore.apply (fresh vertex state is empty anyway).
                currentModels.put(documentPath,
                        model.put(event.objectLocation, VisualVertexModel.empty))

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


    private fun applyCompound(
        documentPath: DocumentPath,
        event: CompoundNotationEvent
    ): PersistentMap<DocumentPath, VisualDataflowModel> {
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
        model: VisualDataflowModel,
        event: CompoundNotationEvent
    ): PersistentMap<DocumentPath, VisualDataflowModel> {
        if (documentPath != event.documentPath) {
            return models
        }

        return when (event) {
            is RenamedDocumentRefactorEvent -> {
                val newModel = model.move(
                        event.removedUnderOldName.documentPath, event.createdWithNewName.destination)

                val removed =
                        models.remove(event.removedUnderOldName.documentPath)
                removed.put(event.createdWithNewName.destination, newModel)
            }

            else ->
                models
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun get(
        host: DocumentPath
    ): VisualDataflowModel {
        val existing = models[host]
        if (existing != null) {
            return existing
        }

        return inspect(host)
    }


    private suspend fun inspect(
        host: DocumentPath
    ): VisualDataflowModel {
        val model = visualDataflowProvider.inspectDataflow(host)
        models = models.put(host, model)
        publishModel(host, model)
        return model
    }


    suspend fun reset(
            host: DocumentPath
    ): VisualDataflowModel {
        val model = visualDataflowProvider.resetDataflow(host)
        models = models.put(host, model)
        publishModel(host, model)
        return model
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun execute(
        host: DocumentPath,
        objectLocation: ObjectLocation,
        waitBeforeRunningMillis: Int = 0,
        waitAfterRunningMillis: Int = 0
    ): VisualVertexTransition {
        if (waitBeforeRunningMillis > 0) {
            delay(waitBeforeRunningMillis.milliseconds)
        }

        willExecute(host, objectLocation)

        if (waitAfterRunningMillis > 0) {
            delay(waitAfterRunningMillis.milliseconds)
        }

        val visualVertexTransition = visualDataflowProvider
                .executeVertex(host, objectLocation)

        didExecute(host, objectLocation, visualVertexTransition)

        return visualVertexTransition
    }


    private suspend fun willExecute(
        host: DocumentPath,
        objectLocation: ObjectLocation
    ) {
        val model = get(host)

        val visualVertexModel = model.vertices[objectLocation]
                ?: return

        val updatedVertex = visualVertexModel.copy(
                running = true,
                error = null)

        val updatedModel = model.put(objectLocation, updatedVertex)

        models = models.put(host, updatedModel)

        publishModel(host, updatedModel)
        publishBeforeExecution(host, objectLocation)
    }


    private suspend fun didExecute(
        host: DocumentPath,
        objectLocation: ObjectLocation,
        visualDataflowTransition: VisualVertexTransition
    ) {
        val model = get(host)

        val visualVertexModel = model.vertices[objectLocation]
                ?: return

        val updatedState = visualDataflowTransition.stateChange
                ?: visualVertexModel.state

        val updatedVertex = VisualVertexModel(
                false,
                updatedState,
                visualDataflowTransition.message,
                visualDataflowTransition.hasNext,
                visualDataflowTransition.iteration,
                visualDataflowTransition.error)

        val updatedModel = model.put(objectLocation, updatedVertex)

        val withStackUnwind = unwindStackIfRequired(updatedModel, visualDataflowTransition)

        models = models.put(host, withStackUnwind)

        publishModel(host, withStackUnwind)
    }


    private fun unwindStackIfRequired(
            updatedModel: VisualDataflowModel,
            visualDataflowTransition: VisualVertexTransition
    ): VisualDataflowModel {
        if (visualDataflowTransition.cleared.isEmpty() && visualDataflowTransition.loop.isEmpty()) {
            return updatedModel
        }

        var cursor = updatedModel

        for (loop in visualDataflowTransition.loop) {
            cursor = cursor.put(
                loop,
                updatedModel.vertices[loop]!!.copy(
                    message = null
                ))
        }

        for (cleared in visualDataflowTransition.cleared) {
            cursor = cursor.put(
                cleared,
                updatedModel.vertices[cleared]!!.copy(
                    message = null,
                    epoch = 0
                ))
        }

        return cursor
    }
}