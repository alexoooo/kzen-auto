package tech.kzen.auto.common.paradigm.dataflow.service.active

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.dataflow.api.Dataflow
import tech.kzen.auto.common.paradigm.dataflow.api.StreamDataflow
import tech.kzen.auto.common.paradigm.dataflow.model.exec.*
import tech.kzen.auto.common.paradigm.dataflow.model.structure.VertexMatrix
import tech.kzen.auto.common.paradigm.dataflow.service.format.DataflowMessageInspector
import tech.kzen.auto.common.service.GraphInstanceManager
import tech.kzen.auto.common.service.GraphStructureManager
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.platform.collect.toPersistentMap


class ActiveDataflowManager(
        private val instanceManager: GraphInstanceManager,
        private val dataflowMessageInspector: DataflowMessageInspector,
        private val graphStructureManager: GraphStructureManager
)//:
//        GraphStructureManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    private var models: MutableMap<DocumentPath, ActiveDataflowModel> = mutableMapOf()


    //-----------------------------------------------------------------------------------------------------------------
//    override suspend fun handleModel(graphStructure: GraphStructure, event: NotationEvent?) {
//
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun get(
            host: DocumentPath
    ): ActiveDataflowModel {
        models[host]?.let {
            return it
        }

        val serverGraphStructure = graphStructureManager.serverGraphStructure()

        check(host in serverGraphStructure.graphNotation.documents)

        val vertexMatrix = VertexMatrix.ofQueryDocument(host, serverGraphStructure.graphNotation)

        val builder = mutableMapOf<ObjectLocation, ActiveVertexModel>()
        for (vertexLocation in vertexMatrix.byLocation().keys) {
            val vertexInstance = instanceManager.get(vertexLocation) as Dataflow<*>
            val initialState = vertexInstance.initialState()

            builder[vertexLocation] = ActiveVertexModel(
                    initialState,
                    null,
                    mutableListOf(),
                    false,
                    0)
        }

        val activeDataflowModel = ActiveDataflowModel(builder)
        models[host] = activeDataflowModel
        return activeDataflowModel
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun inspect(host: DocumentPath): VisualDataflowModel {
        val activeDataflowModel = get(host)

        val builder = mutableMapOf<ObjectLocation, VisualVertexModel>()

        for ((vertexLocation, activeVertexModel) in activeDataflowModel.vertices) {
            val stateInspection = activeVertexModel.state?.let {
                inspectState(vertexLocation, it)
            }

            val messageInspection = activeVertexModel.message
                    ?.let(dataflowMessageInspector::inspectMessage)

            val hasNext = activeVertexModel.streamHasNext ||
                    activeVertexModel.remainingBatch.isNotEmpty()

            builder[vertexLocation] = VisualVertexModel(
                    false,
                    stateInspection,
                    messageInspection,
                    hasNext,
                    activeVertexModel.iterationCount.toInt())
        }

        return VisualDataflowModel(builder.toPersistentMap())
    }


    private suspend fun inspectState(
            vertexLocation: ObjectLocation,
            state: Any
    ): ExecutionValue {
        @Suppress("UNCHECKED_CAST")
        val dataflow = instanceManager.get(vertexLocation) as Dataflow<Any>
        return dataflow.inspectState(state)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun executeVisual(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ): VisualVertexTransition {
        val activeDataflowModel = get(host).vertices[vertexLocation]!!

        val previousStateView = activeDataflowModel.state?.let {
            inspectState(vertexLocation, it)
        }

        execute(host, vertexLocation)

        val nextStateView = activeDataflowModel.state?.let {
            inspectState(vertexLocation, it)
        }

        val stateChange =
                if (previousStateView != nextStateView) {
                    nextStateView
                }
                else {
                    null
                }

        val messageView = activeDataflowModel.message?.let {
            dataflowMessageInspector.inspectMessage(it)
        }

        return VisualVertexTransition(
                stateChange,
                messageView,
                activeDataflowModel.hasNext(),
                activeDataflowModel.iterationCount.toInt())
    }


    suspend fun execute(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ) {
        val activeDataflowModel = get(host).vertices[vertexLocation]!!
        return execute(vertexLocation, activeDataflowModel)
    }


    private suspend fun execute(
            vertexLocation: ObjectLocation,
            activeVertexModel: ActiveVertexModel
    ) {
        if (activeVertexModel.remainingBatch.isNotEmpty()) {
            val nextMessage = activeVertexModel.remainingBatch.removeAt(0)
            activeVertexModel.message = nextMessage
        }
        else {
            @Suppress("UNCHECKED_CAST")
            val dataflow = instanceManager.get(vertexLocation) as Dataflow<Any?>

            // TODO: input and output

            val nextState =
                    if (activeVertexModel.streamHasNext) {
                        (dataflow as StreamDataflow).next(activeVertexModel.state!!)
                    }
                    else {
                        dataflow.process(activeVertexModel.state)
                    }

            activeVertexModel.state = nextState
        }

        activeVertexModel.iterationCount++
    }
}