package tech.kzen.auto.common.paradigm.flow.util

import tech.kzen.auto.common.paradigm.flow.model.exec.VisualFlowModel
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualVertexPhase
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowDag
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure


object FlowUtils {
    //-----------------------------------------------------------------------------------------------------------------
    val mainInputAttributeName = AttributeName("input")
    val mainOutputAttributeName = AttributeName("output")


    fun next(
            host: DocumentPath,
            graphStructure: GraphStructure,
            visualFlowModel: VisualFlowModel
    ): ObjectLocation? {
        val vertexMatrix = FlowMatrix.ofDocument(host, graphStructure)

        val flowDag = FlowDag.of(vertexMatrix)

        return next(vertexMatrix, flowDag, visualFlowModel)
    }


    fun next(
            flowMatrix: FlowMatrix,
            flowDag: FlowDag,
            visualFlowModel: VisualFlowModel
    ): ObjectLocation? {
        var lastLayerInProgress: Int = -1
        var firstLayerReady: Int = -1

        for ((index, layer) in flowDag.layers.withIndex()) {
            if (isLayerInProgress(layer, visualFlowModel)) {
                lastLayerInProgress = index
            }

            if (firstLayerReady == -1 &&
                    isLayerReady(layer, visualFlowModel, flowMatrix, flowDag)) {
                firstLayerReady = index
            }
        }

        val nextLayerIndex = when {
            lastLayerInProgress != -1 ->
                lastLayerInProgress

            firstLayerReady != -1 ->
                firstLayerReady

            else ->
                return null
        }

        val nextLayer = flowDag.layers[nextLayerIndex]

        return nextInLayer(
                nextLayer,
                flowMatrix,
                flowDag,
                visualFlowModel)
    }


    private fun isLayerInProgress(
            layer: List<ObjectLocation>,
            visualFlowModel: VisualFlowModel
    ): Boolean {
        for (vertexLocation in layer) {
            val visualVertexModel = visualFlowModel.vertices[vertexLocation]
                    ?: continue

            if (visualVertexModel.message != null) {
                continue
            }

            if (visualVertexModel.hasNext) {
                return true
            }
        }

        return false
    }


    private fun isLayerReady(
            layer: List<ObjectLocation>,
            visualFlowModel: VisualFlowModel,
            flowMatrix: FlowMatrix,
            flowDag: FlowDag
    ): Boolean {
        for (vertexLocation in layer) {
            val visualVertexModel = visualFlowModel.vertices[vertexLocation]
                    ?: continue

            if (visualVertexModel.epoch != 0) {
                continue
            }

            val vertexDescriptor = flowMatrix.verticesByLocation[vertexLocation]
                    ?: continue

            val predecessors = flowDag.predecessors[vertexLocation]
                    ?: listOf()

            if (vertexDescriptor.inputNames.size != predecessors.size) {
                // TODO: unify with nextInLayer
                continue
            }

            if (predecessors.isEmpty()) {
                return true
            }

            val hasInputsAvailable = predecessors
                    .map { visualFlowModel.vertices[it] }
                    .any { it?.message != null }

            if (hasInputsAvailable) {
                return true
            }
        }

        return false
    }


    private fun nextInLayer(
            layer: List<ObjectLocation>,
            flowMatrix: FlowMatrix,
            flowDag: FlowDag,
            visualFlowModel: VisualFlowModel
    ): ObjectLocation? {
        if (layer.isEmpty()) {
            return null
        }
        else if (layer.size == 1) {
            return layer.first()
        }

        var minEpoch = Int.MAX_VALUE
        var candidate: ObjectLocation? = null

        nextVertex@
        for (vertexLocation in layer) {
            val visualVertexModel = visualFlowModel.vertices[vertexLocation]
                    ?: VisualVertexModel.empty

            val phase = visualVertexModel.phase()

            if (phase != VisualVertexPhase.Pending &&
                    phase != VisualVertexPhase.Remaining) {
                continue
            }

            if (minEpoch <= visualVertexModel.epoch) {
                continue
            }

            val predecessors = flowDag.predecessors[vertexLocation]
                    ?: listOf()

            val vertexDescriptor = flowMatrix.verticesByLocation[vertexLocation]
                    ?: continue

            if (vertexDescriptor.inputNames.size != predecessors.size) {
                // TODO: consider handling optional OptionalInput
                continue
            }

            for (predecessor in predecessors) {
                if (visualFlowModel.vertices[predecessor]?.message == null) {
                    continue@nextVertex
                }
            }

            minEpoch = visualVertexModel.epoch
            candidate = vertexLocation
        }

        return candidate
    }
}