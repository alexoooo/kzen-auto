package tech.kzen.auto.common.paradigm.flow.util

import tech.kzen.auto.common.paradigm.flow.model.exec.VisualFlowModel
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualVertexPhase
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowDag
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.VertexDescriptor
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation


object FlowUtils {
    //-----------------------------------------------------------------------------------------------------------------
    val mainInputAttributeName = AttributeName("input")
    val mainOutputAttributeName = AttributeName("output")


    // NB: deliberately no (documentPath, graphStructure, visualFlowModel) convenience overload — it rebuilt
    // FlowMatrix + FlowDag from notation on every call, which the grid used to pay per cell per render.
    // Routing is derived once per render (FlowController.nonEmptyDag) and threaded down as props.
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
                    isLayerReady(layer, visualFlowModel, flowMatrix)) {
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
            flowMatrix: FlowMatrix
    ): Boolean {
        for (vertexLocation in layer) {
            val visualVertexModel = visualFlowModel.vertices[vertexLocation]
                    ?: continue

            if (visualVertexModel.epoch != 0) {
                continue
            }

            val vertexDescriptor = flowMatrix.verticesByLocation[vertexLocation]
                    ?: continue

            if (inputsReady(vertexDescriptor, flowMatrix, visualFlowModel)) {
                return true
            }
        }

        return false
    }


    /**
     * Per-input readiness, honouring the declared contract:
     * - a required input must be wired (else the vertex is permanently not-ready — the structure
     *   lint reports it before a run gets this far) and its upstream must hold a message;
     * - an optional input never gates on its own, wired or not: layer order guarantees its
     *   upstream has settled by the time this vertex's layer is considered, so an empty wired
     *   optional means the upstream produced nothing this pass (e.g. a filter dropping the
     *   value) and the vertex runs without it — the FizzBuzz SelectLast pattern;
     * - at least one wired input must hold a message (with nothing to consume the vertex isn't
     *   ready); vertices with no inputs (sources) are always ready.
     */
    private fun inputsReady(
            vertexDescriptor: VertexDescriptor,
            flowMatrix: FlowMatrix,
            visualFlowModel: VisualFlowModel
    ): Boolean {
        var anyMessage = false

        for (inputName in vertexDescriptor.inputNames) {
            val wiredPredecessor = flowMatrix.traceVertexBackFrom(vertexDescriptor, inputName)

            if (wiredPredecessor == null) {
                if (inputName in vertexDescriptor.requiredInputNames) {
                    return false
                }
                continue
            }

            if (visualFlowModel.vertices[wiredPredecessor.objectLocation]?.message != null) {
                anyMessage = true
            }
            else if (inputName in vertexDescriptor.requiredInputNames) {
                return false
            }
        }

        return anyMessage || vertexDescriptor.inputNames.isEmpty()
    }


    private fun nextInLayer(
            layer: List<ObjectLocation>,
            flowMatrix: FlowMatrix,
            visualFlowModel: VisualFlowModel
    ): ObjectLocation? {
        if (layer.isEmpty()) {
            return null
        }
        else if (layer.size == 1) {
            // Error-only guard: an errored (parked) vertex must not display as next-to-run. Deliberately no
            // inputsReady / other-phase gating here — a mid-stream vertex re-executes without fresh inputs
            // (see inProgressSingleVertexLayerSelectedWithoutInputCheck). Server routing never sees the Error
            // phase (FlowRun.snapshotVisual passes error = null), so this is client-only.
            val only = layer.first()
            val onlyPhase = (visualFlowModel.vertices[only] ?: VisualVertexModel.empty).phase()
            return if (onlyPhase == VisualVertexPhase.Error) null else only
        }

        var minEpoch = Int.MAX_VALUE
        var candidate: ObjectLocation? = null

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

            val vertexDescriptor = flowMatrix.verticesByLocation[vertexLocation]
                    ?: continue

            if (! inputsReady(vertexDescriptor, flowMatrix, visualFlowModel)) {
                continue
            }

            minEpoch = visualVertexModel.epoch
            candidate = vertexLocation
        }

        return candidate
    }
}