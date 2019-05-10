package tech.kzen.auto.common.paradigm.dataflow.util

import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
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


//    private enum class LayerClassification {
//        NotStarted,
//        InProgress,
//        Finished
//    }


    fun next(
            host: DocumentPath,
            graphNotation: GraphNotation,
            visualDataflowModel: VisualDataflowModel
    ): ObjectLocation? {
        val vertexMatrix = VertexMatrix.ofQueryDocument(host, graphNotation)
//        println("^^^^^ next: vertexMatrix - $vertexMatrix")

        val dataflowDag = DataflowDag.of(vertexMatrix)

        return next(dataflowDag, visualDataflowModel)
    }

    fun next(
//            host: DocumentPath,
            dataflowDag: DataflowDag,
            visualDataflowModel: VisualDataflowModel
    ): ObjectLocation? {
//        println("^^^^^ next: visualDataflowModel - $visualDataflowModel")

//        val vertexMatrix = VertexMatrix.ofQueryDocument(host, graphNotation)
//        println("^^^^^ next: vertexMatrix - $vertexMatrix")

//        val dataflowDag = DataflowDag.of(vertexMatrix)
//        println("^^^^^ next: dataflowDag - $dataflowDag")

        var lastLayerInProgress: Int = -1
        var firstLayerReady: Int = -1

        for ((index, layer) in dataflowDag.layers.withIndex()) {
            if (isLayerInProgress(layer, visualDataflowModel)) {
                lastLayerInProgress = index
            }

            if (firstLayerReady == -1 &&
                    isLayerReady(layer, visualDataflowModel, dataflowDag)) {
                firstLayerReady = index
            }
        }
//        println("^^^^^ next: dataflowDag - $lastLayerInProgress / $firstLayerReady")

        val nextLayerIndex = when {
            lastLayerInProgress != -1 ->
                lastLayerInProgress

            firstLayerReady != -1 ->
                firstLayerReady

            else ->
                return null
        }

        val nextLayer = dataflowDag.layers[nextLayerIndex]

        return nextInLayer(nextLayer, visualDataflowModel)
    }


    private fun isLayerInProgress(
            layer: List<ObjectLocation>,
            visualDataflowModel: VisualDataflowModel
    ): Boolean {
        for (vertexLocation in layer) {
            val visualVertexModel = visualDataflowModel.vertices[vertexLocation]
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
            visualDataflowModel: VisualDataflowModel,
            dataflowDag: DataflowDag
    ): Boolean {
        for (vertexLocation in layer) {
            val visualVertexModel = visualDataflowModel.vertices[vertexLocation]
                    ?: continue

            if (visualVertexModel.epoch != 0) {
                continue
            }

            val predecessors = dataflowDag.predecessors[vertexLocation]
                    ?: return true

//            println("^^^^^^ isLayerReady $visualVertexModel - $predecessors")

            if (predecessors.isEmpty()) {
                return true
            }

            val hasInputsAvailable = predecessors
                    .map { visualDataflowModel.vertices[it] }
                    .any { it?.message != null }

            if (hasInputsAvailable) {
                return true
            }
        }

        return false
    }


//    private fun classifyLayer(
//            layer: List<ObjectLocation>,
//            visualDataflowModel: VisualDataflowModel
//    ): LayerClassification {
//        val doneCount = layer.count {
//            val visualVertexModel = visualDataflowModel.vertices[it]
//                    ?: VisualVertexModel.empty
//
//            visualVertexModel.phase() == VisualVertexPhase.Done
//        }
//
//        return when {
//            doneCount == layer.size ->
//                DataflowUtils.LayerClassification.Finished
//
//            doneCount >= 1 ->
//                DataflowUtils.LayerClassification.InProgress
//
//            else ->
//                DataflowUtils.LayerClassification.NotStarted
//        }
//    }


    private fun nextInLayer(
            layer: List<ObjectLocation>,
            visualDataflowModel: VisualDataflowModel
    ): ObjectLocation? {
        if (layer.size == 1) {
            return layer.first()
        }

        TODO()
//        for (vertexLocation in layer) {
//            val visualVertexModel = visualDataflowModel.vertices[vertexLocation]
//                    ?: VisualVertexModel.empty
//
//            if (visualVertexModel.phase() == VisualVertexPhase.Pending) {
//                return vertexLocation
//            }
//        }
//        return null
    }
}