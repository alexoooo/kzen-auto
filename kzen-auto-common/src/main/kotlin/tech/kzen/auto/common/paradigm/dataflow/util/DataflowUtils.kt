package tech.kzen.auto.common.paradigm.dataflow.util

import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexPhase
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowDag
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowMatrix
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure


object DataflowUtils {
    //-----------------------------------------------------------------------------------------------------------------
    val mainInputAttributeName = AttributeName("input")
    val mainOutputAttributeName = AttributeName("output")


//    private enum class LayerClassification {
//        NotStarted,
//        InProgress,
//        Finished
//    }


    fun next(
            host: DocumentPath,
            graphStructure: GraphStructure,
            visualDataflowModel: VisualDataflowModel
    ): ObjectLocation? {
        val vertexMatrix = DataflowMatrix.ofQueryDocument(host, graphStructure)
//        println("^^^^^ next: vertexMatrix - $vertexMatrix")

        val dataflowDag = DataflowDag.of(vertexMatrix)

        return next(vertexMatrix, dataflowDag, visualDataflowModel)
    }


    fun next(
            dataflowMatrix: DataflowMatrix,
            dataflowDag: DataflowDag,
            visualDataflowModel: VisualDataflowModel
    ): ObjectLocation? {
//        println("^^^^^ next: dataflowDag.layers - ${dataflowDag.layers}")
        var lastLayerInProgress: Int = -1
        var firstLayerReady: Int = -1

        for ((index, layer) in dataflowDag.layers.withIndex()) {
            if (isLayerInProgress(layer, visualDataflowModel)) {
                lastLayerInProgress = index
            }

            if (firstLayerReady == -1 &&
                    isLayerReady(layer, visualDataflowModel, dataflowMatrix, dataflowDag)) {
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

//        println("^^^^^ got nextLayer: $nextLayer")
        return nextInLayer(
                nextLayer,
                dataflowMatrix,
                dataflowDag,
                visualDataflowModel)
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
            dataflowMatrix: DataflowMatrix,
            dataflowDag: DataflowDag
    ): Boolean {
        for (vertexLocation in layer) {
//            println("^^^^^^ isLayerReady vertexLocation $vertexLocation")

            val visualVertexModel = visualDataflowModel.vertices[vertexLocation]
                    ?: continue

            if (visualVertexModel.epoch != 0) {
                continue
            }

            val vertexDescriptor = dataflowMatrix.verticesByLocation[vertexLocation]
                    ?: continue

            val predecessors = dataflowDag.predecessors[vertexLocation]
                    ?: listOf()

            if (vertexDescriptor.inputNames.size != predecessors.size) {
                // TODO: unify with nextInLayer
                continue
            }

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
            dataflowMatrix: DataflowMatrix,
            dataflowDag: DataflowDag,
            visualDataflowModel: VisualDataflowModel
    ): ObjectLocation? {
        if (layer.isEmpty()) {
            return null
        }
        else if (layer.size == 1) {
            return layer.first()
        }

        var minEpoch = Int.MAX_VALUE
        var candidate: ObjectLocation? = null

//        nextVertex@
        for (vertexLocation in layer) {
            val visualVertexModel = visualDataflowModel.vertices[vertexLocation]
                    ?: VisualVertexModel.empty

            val phase = visualVertexModel.phase()

            if (phase != VisualVertexPhase.Pending &&
                    phase != VisualVertexPhase.Remaining) {
                continue
            }

//            println("^^^^ Looking at ($minEpoch - ${visualVertexModel.epoch}): $vertexLocation")
            if (minEpoch <= visualVertexModel.epoch) {
                continue
            }

            val predecessors = dataflowDag.predecessors[vertexLocation]
                    ?: listOf()

            val vertexDescriptor = dataflowMatrix.verticesByLocation[vertexLocation]
                    ?: continue

//            println("^^^^ Inputs: ${vertexDescriptor.inputNames.size} vs ${predecessors.size}")
            if (vertexDescriptor.inputNames.size != predecessors.size) {
                // TODO: consider handling optional OptionalInput
                continue
            }

//            println("^^^^^&& predecessors - $predecessors")

            for (predecessor in predecessors) {
                if (visualDataflowModel.vertices[predecessor]?.message == null) {
                    continue//@nextVertex
                }
            }

            minEpoch = visualVertexModel.epoch
            candidate = vertexLocation
        }

//        println("^^^^^ next is: $candidate")
        return candidate
    }
}