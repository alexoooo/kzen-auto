package tech.kzen.auto.common.paradigm.dataflow.service.visual

import kotlinx.coroutines.delay
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.persistentMapOf


class VisualDataflowManager(
        private val visualDataflowProvider: VisualDataflowProvider
) {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        suspend fun beforeDataflowExecution(host: DocumentPath, vertexLocation: ObjectLocation)
        suspend fun onVisualDataflowModel(host: DocumentPath, visualDataflowModel: VisualDataflowModel)
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private val observers = mutableSetOf<Pair<DocumentPath, Observer>>()
    private val observers = mutableSetOf<Observer>()
    private var models: PersistentMap<DocumentPath, VisualDataflowModel> = persistentMapOf()


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun observe(/*host: DocumentPath,*/ observer: Observer) {
//        observers.add(host to observer)
        observers.add(observer)

        for ((host, model) in models) {
            observer.onVisualDataflowModel(host, model)
        }

//        val model = models[host]
//        if (model != null) {
//            observer.onVisualDataflowModel(host, model)
//        }
//        else {
//            initiateModel(host)
//        }
    }


    fun unobserve(observer: Observer) {
        observers.removeAll { it == observer }
    }


    suspend fun ping(host: DocumentPath) {
        if (host in models) {
            return
        }

        inspect(host)
    }


    private suspend fun publishBeforeExecution(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ) {
        for (observer in observers) {
//            if (host != hostKey) {
//                continue
//            }

            observer.beforeDataflowExecution(host, vertexLocation)
        }
    }


    private suspend fun publishModel(
            host: DocumentPath,
            model: VisualDataflowModel
    ) {
//        for ((hostKey, observer) in observers) {
        for (observer in observers) {
//            if (host != hostKey) {
//                continue
//            }

            observer.onVisualDataflowModel(host, model)
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
        val model = visualDataflowProvider.inspect(host)
        models = models.put(host, model)
        publishModel(host, model)
        return model
    }


    suspend fun reset(
            host: DocumentPath
    ): VisualDataflowModel {
        val model = visualDataflowProvider.reset(host)
        models = models.put(host, model)
        publishModel(host, model)
        return model
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

        val visualVertexTransition = visualDataflowProvider
                .execute(host, objectLocation)

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
//        println("^^^^^^ didExecute - $visualDataflowTransition")

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
                visualDataflowTransition.iteration)

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