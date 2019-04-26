package tech.kzen.auto.common.paradigm.dataflow.service.visual

import kotlinx.coroutines.delay
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.persistentMapOf


class VisualDataflowManager(
        private val visualDataflowInitializer: VisualDataflowInitializer,
        private val visualDataflowExecutor: VisualDataflowExecutor
) {
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

        for (model in models) {
            observer.onVisualDataflowModel(model.key, model.value)
        }
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    private suspend fun publishBeforeExecution(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ) {
        for (subscriber in observers) {
            subscriber.beforeDataflowExecution(host, vertexLocation)
        }
    }


    private suspend fun publishModel(
            host: DocumentPath,
            model: VisualDataflowModel
    ) {
        for (subscriber in observers) {
            subscriber.onVisualDataflowModel(host, model)
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

        val initial = visualDataflowInitializer.initialModel(host)
        models = models.put(host, initial)
        publishModel(host, initial)
        return initial
    }


    suspend fun reset(
            host: DocumentPath
    ): Digest {
        models = models.remove(host)
        return get(host).digest()
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun execute(
            host: DocumentPath,
            objectLocation: ObjectLocation,
            waitAfterRunningMillis: Int = 0
    ): VisualVertexTransition {
        willExecute(host, objectLocation)

        if (waitAfterRunningMillis > 0) {
            delay(waitAfterRunningMillis.toLong())
        }

        val visualVertexTransition = visualDataflowExecutor.execute(host, objectLocation)

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

        val updatedVertex = visualVertexModel.copy(running = true)
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
                visualDataflowTransition.hasNext)

        val updatedModel = model.put(objectLocation, updatedVertex)

        models = models.put(host, updatedModel)

        publishModel(host, updatedModel)
    }
}