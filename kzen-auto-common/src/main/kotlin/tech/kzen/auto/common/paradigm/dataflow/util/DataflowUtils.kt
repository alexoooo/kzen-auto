package tech.kzen.auto.common.paradigm.dataflow.util

import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexPhase
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowDag
import tech.kzen.auto.common.paradigm.dataflow.model.structure.VertexMatrix
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.notation.model.GraphNotation


object DataflowUtils {
    //-----------------------------------------------------------------------------------------------------------------
    val inputAttributeName = AttributeName("input")
    val outputAttributeName = AttributeName("output")


    private enum class LayerClassification {
        NotStarted,
        InProgress,
        Finished
    }


    fun next(
            host: DocumentPath,
            graphNotation: GraphNotation,
            visualDataflowModel: VisualDataflowModel
    ): ObjectLocation? {
        val vertexMatrix = VertexMatrix.ofQueryDocument(host, graphNotation)
//        println("^^^^^ next: vertexMatrix - $vertexMatrix")

        val dataflowDag = DataflowDag.of(vertexMatrix)
//        println("^^^^^ next: dataflowDag - $dataflowDag")

        var lastLayerInProgress: Int = -1
        var lastFinishedLayer: Int = -1

        for ((index, layer) in dataflowDag.layers.withIndex()) {
            val layerClassification = classifyLayer(layer, visualDataflowModel)

            if (layerClassification == LayerClassification.InProgress) {
                lastLayerInProgress = index
            }

            if (layerClassification == LayerClassification.Finished) {
                lastFinishedLayer = index
            }
        }
//        println("^^^^^ next: dataflowDag - $lastLayerInProgress / $lastFinishedLayer")

        val nextLayerIndex = when {
            lastLayerInProgress != -1 ->
                lastLayerInProgress

            lastFinishedLayer != -1 ->
                lastFinishedLayer + 1

            else ->
                0
        }
        val nextLayer = dataflowDag.layers[nextLayerIndex]

        return firstPending(nextLayer, visualDataflowModel)
    }


    private fun classifyLayer(
            layer: List<ObjectLocation>,
            visualDataflowModel: VisualDataflowModel
    ): LayerClassification {
        val doneCount = layer.count {
            val visualVertexModel = visualDataflowModel.vertices[it]
                    ?: VisualVertexModel.empty

            visualVertexModel.phase() == VisualVertexPhase.Done
        }

        return when {
            doneCount == layer.size ->
                DataflowUtils.LayerClassification.Finished

            doneCount >= 1 ->
                DataflowUtils.LayerClassification.InProgress

            else ->
                DataflowUtils.LayerClassification.NotStarted
        }
    }


    private fun firstPending(
            layer: List<ObjectLocation>,
            visualDataflowModel: VisualDataflowModel
    ): ObjectLocation? {
        for (vertexLocation in layer) {
            val visualVertexModel = visualDataflowModel.vertices[vertexLocation]
                    ?: VisualVertexModel.empty

            if (visualVertexModel.phase() == VisualVertexPhase.Pending) {
                return vertexLocation
            }
        }
        return null
    }
}