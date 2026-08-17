package tech.kzen.auto.client.objects.document.flow

import tech.kzen.auto.common.paradigm.flow.model.exec.VisualFlowModel
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowDag
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.VertexDescriptor
import tech.kzen.lib.common.model.location.ObjectLocation


// Grid-global edge tinting sets, derived ONCE per render by FlowController and threaded down as a prop —
// the same discipline FlowController.nonEmptyDag applies to nextToRun. Every member here is a function of
// the whole grid, so deriving them per edge cell cost O(V*E)-scale work per cell per paint.
class FlowEdgeRouting(
    // A running vertex takes precedence over the pure routing pick, which is what tints the pipes feeding
    // the vertex being executed rather than the one queued behind it.
    val nextToRun: ObjectLocation?,

    val edgesLeadingToNextToRun: Set<EdgeDescriptor>,
    val edgesInFlightToPending: Set<EdgeDescriptor>,
    val pendingWithAvailableMessage: Set<ObjectLocation>,
    val edgesAvailableToPending: Set<EdgeDescriptor>,
    val edgesCarryingMessage: Set<EdgeDescriptor>
) {
    companion object {
        fun of(
            visualFlowModel: VisualFlowModel,
            flowMatrix: FlowMatrix,
            flowDag: FlowDag,
            nextToRun: ObjectLocation?
        ): FlowEdgeRouting {
            val pendingToRun = nextToRun?.let {
                pendingToRunVertexDescriptor(visualFlowModel, flowMatrix, it)
            }

            val pendingWithAvailableMessage = pendingWithAvailableMessage(visualFlowModel, flowDag)

            return FlowEdgeRouting(
                nextToRun,
                pendingToRun
                    ?.let { edgesLeadingTo(visualFlowModel, flowMatrix, it) }
                    ?: setOf(),
                pendingToRun
                    ?.let { edgesFlowingToPending(visualFlowModel, flowMatrix, flowDag, it) }
                    ?: setOf(),
                pendingWithAvailableMessage,
                edgesAvailableToPending(visualFlowModel, flowMatrix, pendingWithAvailableMessage),
                edgesCarryingMessage(visualFlowModel, flowMatrix))
        }


        private fun pendingToRunVertexDescriptor(
            visualFlowModel: VisualFlowModel,
            flowMatrix: FlowMatrix,
            nextToRun: ObjectLocation
        ): VertexDescriptor? {
            val nextToRunVisualVertexModel = visualFlowModel.vertices[nextToRun]
                ?: return null

            if (nextToRunVisualVertexModel.epoch > 0) {
                return null
            }

            // NB: might be null when navigating to new document while running
            return flowMatrix.verticesByLocation[nextToRun]
        }


        private fun edgesFlowingToPending(
            visualFlowModel: VisualFlowModel,
            flowMatrix: FlowMatrix,
            flowDag: FlowDag,
            nextToRun: VertexDescriptor
        ): Set<EdgeDescriptor> {
            val builder = mutableSetOf<EdgeDescriptor>()

            for ((objectLocation, vertexVisualModel) in visualFlowModel.vertices) {
                if (vertexVisualModel.message == null) {
                    continue
                }

                val pendingSuccessors = pendingSuccessors(visualFlowModel, flowDag, objectLocation)
                if (nextToRun.objectLocation in pendingSuccessors) {
                    continue
                }

                for (pendingSuccessor in pendingSuccessors) {
                    val successorVertexDescriptor = flowMatrix.verticesByLocation[pendingSuccessor]
                        ?: throw IllegalStateException()

                    val edgesToSuccessor = edgesLeadingTo(visualFlowModel, flowMatrix, successorVertexDescriptor)

                    val successorsUpToNextToRun = edgesToSuccessor
                        .filter { it.coordinate.row <= nextToRun.coordinate.row }

                    builder.addAll(successorsUpToNextToRun)
                }
            }

            return builder
        }


        private fun pendingWithAvailableMessage(
            visualFlowModel: VisualFlowModel,
            flowDag: FlowDag
        ): Set<ObjectLocation> {
            return visualFlowModel.vertices
                .filter { it.value.message != null }
                .flatMap { pendingSuccessors(visualFlowModel, flowDag, it.key) }
                .toSet()
        }


        private fun edgesAvailableToPending(
            visualFlowModel: VisualFlowModel,
            flowMatrix: FlowMatrix,
            pendingWithAvailableMessage: Collection<ObjectLocation>
        ): Set<EdgeDescriptor> {
            return pendingWithAvailableMessage
                .mapNotNull { flowMatrix.verticesByLocation[it] }
                .flatMap { edgesLeadingTo(visualFlowModel, flowMatrix, it) }
                .toSet()
        }


        private fun pendingSuccessors(
            visualFlowModel: VisualFlowModel,
            flowDag: FlowDag,
            objectLocation: ObjectLocation
        ): List<ObjectLocation> {
            val successors = flowDag.successors[objectLocation]
                ?: return listOf()

            val builder = mutableListOf<ObjectLocation>()

            for (successor in successors) {
                val successorVisualVertexModel = visualFlowModel.vertices[successor]
                    ?: continue

                if (successorVisualVertexModel.epoch == 0) {
                    builder.add(successor)
                }
            }

            return builder
        }


        private fun edgesLeadingTo(
            visualFlowModel: VisualFlowModel,
            flowMatrix: FlowMatrix,
            nextToRun: VertexDescriptor
        ): Set<EdgeDescriptor> {
            val buffer = mutableSetOf<EdgeDescriptor>()
            for ((i, inputName) in nextToRun.inputNames.withIndex()) {
                val sourceVertex = flowMatrix.traceVertexBackFrom(nextToRun, inputName)
                    ?: continue

                val sourceVisualModel = visualFlowModel.vertices[sourceVertex.objectLocation]
                    ?: continue

                if (sourceVisualModel.message == null) {
                    continue
                }

                buffer.addAll(flowMatrix.traceEdgeBackFrom(nextToRun, i))
            }
            return buffer
        }


        // Every edge currently carrying a message: leading to any vertex (pending or already-run) fed by a
        // source that still holds its message. Superset of the sending / in-flight / available sets; the
        // colour when-chains test those stronger categories first, so this only colours the "already
        // traversed" upstream segments of the active path — the message went down them but its consumer has
        // already run (epoch > 0), which the pending-only sets miss and leave white.
        private fun edgesCarryingMessage(
            visualFlowModel: VisualFlowModel,
            flowMatrix: FlowMatrix
        ): Set<EdgeDescriptor> {
            val builder = mutableSetOf<EdgeDescriptor>()
            for (vertexDescriptor in flowMatrix.verticesByLocation.values) {
                builder.addAll(edgesLeadingTo(visualFlowModel, flowMatrix, vertexDescriptor))
            }
            return builder
        }
    }
}
