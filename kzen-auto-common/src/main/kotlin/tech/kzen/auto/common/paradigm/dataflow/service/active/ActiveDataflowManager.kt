package tech.kzen.auto.common.paradigm.dataflow.service.active

import tech.kzen.auto.common.paradigm.dataflow.api.Dataflow
import tech.kzen.auto.common.paradigm.dataflow.api.StreamDataflow
import tech.kzen.auto.common.paradigm.dataflow.model.exec.*
import tech.kzen.auto.common.paradigm.dataflow.service.format.DataflowMessageInspector
import tech.kzen.auto.common.service.GraphInstanceManager
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.platform.collect.toPersistentMap


class ActiveDataflowManager(
        private val instanceManager: GraphInstanceManager,
        private val dataflowMessageInspector: DataflowMessageInspector
) {
    //-----------------------------------------------------------------------------------------------------------------
    private var models: MutableMap<DocumentPath, ActiveDataflowModel> = mutableMapOf()


    //-----------------------------------------------------------------------------------------------------------------
    fun inspect(host: DocumentPath): VisualDataflowModel {
        val activeDataflowModel = models[host]
                ?: return VisualDataflowModel.empty

        val builder = mutableMapOf<ObjectLocation, VisualVertexModel>()

        for ((vertexLocation, activeVertexModel) in activeDataflowModel.vertices) {
            val stateInspection = activeVertexModel.state?.let {
                @Suppress("UNCHECKED_CAST")
                val dataflow = instanceManager.get(vertexLocation) as Dataflow<Any>
                dataflow.inspectState(it)
            }

            val messageInspection = activeVertexModel.message
                    ?.let(dataflowMessageInspector::inspectMessage)

            val hasNext = activeVertexModel.streamHasNext ||
                    activeVertexModel.remainingBatch.isNotEmpty()

            builder[vertexLocation] = VisualVertexModel(
                    false,
                    stateInspection,
                    messageInspection,
                    hasNext)
        }

        return VisualDataflowModel(builder.toPersistentMap())
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun executeVisual(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ): VisualVertexTransition {
        val activeDataflowModel = models[host]!!.vertices[vertexLocation]!!

        execute(host, vertexLocation)

        TODO()
//        return VisualVertexTransition(
//                // TODO
//        )
    }


    fun execute(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ) {
        val activeDataflowModel = models[host]!!.vertices[vertexLocation]!!
        return execute(vertexLocation, activeDataflowModel)
    }


    private fun execute(
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
            if (activeVertexModel.streamHasNext) {
                val nextState = (dataflow as StreamDataflow).next(activeVertexModel.state!!)
                activeVertexModel.state = nextState
            }
            else {
                val nextState = dataflow.process(activeVertexModel.state)
                activeVertexModel.state = nextState
            }
        }
    }
}