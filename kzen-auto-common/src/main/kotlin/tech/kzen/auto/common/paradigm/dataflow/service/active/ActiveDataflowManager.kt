package tech.kzen.auto.common.paradigm.dataflow.service.active

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.dataflow.api.Dataflow
import tech.kzen.auto.common.paradigm.dataflow.api.StreamDataflow
import tech.kzen.auto.common.paradigm.dataflow.model.chanel.MutableDataflowOutput
import tech.kzen.auto.common.paradigm.dataflow.model.chanel.MutableRequiredInput
import tech.kzen.auto.common.paradigm.dataflow.model.exec.*
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowDag
import tech.kzen.auto.common.paradigm.dataflow.model.structure.VertexMatrix
import tech.kzen.auto.common.paradigm.dataflow.service.format.DataflowMessageInspector
import tech.kzen.auto.common.paradigm.dataflow.util.DataflowUtils
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
//    //-----------------------------------------------------------------------------------------------------------------
//    private class Handle(
//            val input: ObjectLocation?,
//            val output: ObjectLocation?
//    )


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
            val vertexInstance = instanceManager.get(vertexLocation).reference as Dataflow<*>

            val initialState = vertexInstance.initialState()
            val initialStateOrNull =
                    if (initialState == Unit) {
                        null
                    }
                    else {
                        initialState
                    }

            builder[vertexLocation] = ActiveVertexModel(
                    initialStateOrNull,
                    null,
                    mutableListOf(),
                    false,
                    0)
        }

        val dataflowDag = DataflowDag.of(vertexMatrix)

        val activeDataflowModel = ActiveDataflowModel(builder, dataflowDag)
        models[host] = activeDataflowModel
        return activeDataflowModel
    }


//    private fun buildHandle(): Handle {
//
//    }


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

        return VisualDataflowModel(
                builder.toPersistentMap()/*,
                activeDataflowModel.dataflowDag*/)
    }


    private suspend fun inspectState(
            vertexLocation: ObjectLocation,
            state: Any
    ): ExecutionValue {
        @Suppress("UNCHECKED_CAST")
        val dataflow = instanceManager.get(vertexLocation).reference as Dataflow<Any>
        return dataflow.inspectState(state)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun executeVisual(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ): VisualVertexTransition {
        val activeDataflowModel = get(host)
        val activeVertexModel = activeDataflowModel.vertices[vertexLocation]!!

        val previousStateView = activeVertexModel.state?.let {
            inspectState(vertexLocation, it)
        }

        execute(host, vertexLocation, activeDataflowModel.dataflowDag)

        val nextStateView = activeVertexModel.state?.let {
            inspectState(vertexLocation, it)
        }

        val stateChange =
                if (previousStateView != nextStateView) {
                    nextStateView
                }
                else {
                    null
                }

        val messageView = activeVertexModel.message?.let {
            dataflowMessageInspector.inspectMessage(it)
        }

        return VisualVertexTransition(
                stateChange,
                messageView,
                activeVertexModel.hasNext(),
                activeVertexModel.iterationCount.toInt(),
                listOf())
    }


    suspend fun execute(
            host: DocumentPath,
            vertexLocation: ObjectLocation,
            dataflowDag: DataflowDag
    ) {
        val activeDataflowModel = get(host)
        val activeVertexModel = activeDataflowModel.vertices[vertexLocation]!!

        if (activeVertexModel.remainingBatch.isNotEmpty()) {
            val nextMessage = activeVertexModel.remainingBatch.removeAt(0)
            activeVertexModel.message = nextMessage
        }
        else {
            val instance = instanceManager.get(vertexLocation)

            @Suppress("UNCHECKED_CAST")
            val dataflow = instance.reference as Dataflow<Any?>

            val input = instance.constructorAttributes[DataflowUtils.inputAttributeName] as? MutableRequiredInput<*>
            if (input != null) {
                val inputLocation: ObjectLocation =
                        dataflowDag.predecessors[vertexLocation]?.first()!!

                val inputActiveModel = activeDataflowModel.vertices[inputLocation]!!

                val message = inputActiveModel.message
                input.set(message)
            }

            val nextState =
                    when {
                        activeVertexModel.streamHasNext ->
                            (dataflow as StreamDataflow).next(activeVertexModel.state!!)

                        activeVertexModel.state == null -> {
                            dataflow.process(Unit)
                            null
                        }

                        else ->
                            dataflow.process(activeVertexModel.state)
                    }

            val output = instance.constructorAttributes[DataflowUtils.outputAttributeName] as? MutableDataflowOutput<*>
            if (output != null) {
                if (output.bufferHasMultiple()) {
                    output.consumeAndClear {
                        if (activeVertexModel.message == null) {
                            activeVertexModel.message = it
                        }
                        else {
                            activeVertexModel.remainingBatch.add(it!!)
                        }
                    }
                }
                else {
                    activeVertexModel.message = output.getAndClear()
                }
                activeVertexModel.streamHasNext = output.streamHasNext()
            }

            activeVertexModel.state = nextState
        }

        activeVertexModel.iterationCount++

//        TODO("detect and and clear back up to in progress layer")

//        if
//        activeVertexModel.hasNext()
    }
}