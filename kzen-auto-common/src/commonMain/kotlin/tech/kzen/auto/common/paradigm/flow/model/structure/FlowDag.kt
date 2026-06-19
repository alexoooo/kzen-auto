package tech.kzen.auto.common.paradigm.flow.model.structure

import tech.kzen.auto.common.paradigm.flow.model.structure.cell.CellDescriptor
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.VertexDescriptor
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.platform.collect.toPersistentList


data class FlowDag(
        val successors: Map<ObjectLocation, List<ObjectLocation>>,
        val predecessors: Map<ObjectLocation, List<ObjectLocation>>,
        val layers: List<List<ObjectLocation>>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun of(flowMatrix: FlowMatrix): FlowDag {
            val vertexMap = flowMatrix.verticesByLocation
            val successors = successors(flowMatrix, vertexMap)
            val predecessors = predecessors(successors)

//            val layers = flowMatrix
//                    .rows
//                    .map { row -> row
//                            .mapNotNull { it as? VertexDescriptor }
//                            .map { it.objectLocation }
//                    }

            val layers = layers(successors, vertexMap, predecessors)

            return FlowDag(successors, predecessors, layers)
        }


        private fun successors(
                flowMatrix: FlowMatrix,
                vertexMap: Map<ObjectLocation, VertexDescriptor>
        ): Map<ObjectLocation, List<ObjectLocation>> {
            val builder = mutableMapOf<ObjectLocation, List<ObjectLocation>>()

            for (vertexInfo in vertexMap.values) {
                val successorBuilder = mutableListOf<ObjectLocation>()

                for (successor in vertexSuccessors(vertexInfo, flowMatrix)) {
                    successorBuilder.add(successor)
                }

                builder[vertexInfo.objectLocation] = successorBuilder.toPersistentList()
            }

            return builder
        }


        private fun vertexSuccessors(
                vertexDescriptor: VertexDescriptor,
                flowMatrix: FlowMatrix
        ): List<ObjectLocation> {
            @Suppress("MoveVariableDeclarationIntoWhen")
            val cellBelow = findCellBelow(
                    vertexDescriptor, flowMatrix
            ) ?: return listOf()

            return when (cellBelow) {
                is VertexDescriptor ->
                    listOf(cellBelow.objectLocation)

                is EdgeDescriptor ->
                    traceEdge(cellBelow, flowMatrix)
            }
        }


        private fun traceEdge(
                edgeDescriptor: EdgeDescriptor,
                flowMatrix: FlowMatrix
        ): List<ObjectLocation> {
            if (!edgeDescriptor.orientation.hasTop()) {
                return listOf()
            }

            val buffer = mutableListOf<ObjectLocation>()
            traceEdge(edgeDescriptor, flowMatrix, buffer)
            return buffer
        }


        private fun traceEdge(
                edgeDescriptor: EdgeDescriptor,
                flowMatrix: FlowMatrix,
                buffer: MutableList<ObjectLocation>
        ) {
            if (edgeDescriptor.orientation.hasBottom()) {
                traceCellBelow(edgeDescriptor, flowMatrix, buffer)
            }

            if (edgeDescriptor.orientation.hasRightEgress()) {
                @Suppress("MoveVariableDeclarationIntoWhen")
                val cellRight = flowMatrix.get(
                        edgeDescriptor.coordinate.row,
                        edgeDescriptor.coordinate.column + 1)

                if (cellRight is EdgeDescriptor &&
                        cellRight.orientation.hasLeftIngress()) {
                    traceEdge(cellRight, flowMatrix, buffer)
                }
            }

            if (edgeDescriptor.orientation.hasLeftEgress()) {
                @Suppress("MoveVariableDeclarationIntoWhen")
                val cellLeft = flowMatrix.get(
                        edgeDescriptor.coordinate.row,
                        edgeDescriptor.coordinate.column - 1)

                if (cellLeft is EdgeDescriptor &&
                        cellLeft.orientation.hasRightIngress()) {
                    traceEdge(cellLeft, flowMatrix, buffer)
                }
            }
        }

        private fun findCellBelow(
                cellDescriptor: CellDescriptor,
                flowMatrix: FlowMatrix
        ): CellDescriptor? {
            @Suppress("MoveVariableDeclarationIntoWhen")
            val cellBelow = flowMatrix.get(
                    cellDescriptor.coordinate.row + 1,
                    cellDescriptor.coordinate.column)

            when (cellBelow) {
                is VertexDescriptor ->
                    return cellBelow

                is EdgeDescriptor ->
                    return cellBelow

                null -> {
                    for (i in cellDescriptor.coordinate.column - 1 downTo 0) {
                        val possibleMultiCell = flowMatrix.get(
                                cellDescriptor.coordinate.row + 1,
                                i
                        ) ?: continue

                        if (possibleMultiCell is VertexDescriptor) {
                            if (possibleMultiCell.inputNames.size > cellDescriptor.coordinate.column - i) {
                                return possibleMultiCell
                            }
                            else {
                                break
                            }
                        }
                    }
                }
            }

            return null
        }


        private fun traceCellBelow(
                edgeDescriptor: EdgeDescriptor,
                flowMatrix: FlowMatrix,
                buffer: MutableList<ObjectLocation>
        ) {
            @Suppress("MoveVariableDeclarationIntoWhen")
            val cellBelow = findCellBelow(edgeDescriptor, flowMatrix)

            when (cellBelow) {
                is VertexDescriptor ->
                    buffer.add(cellBelow.objectLocation)

                is EdgeDescriptor ->
                    if (cellBelow.orientation.hasTop()) {
                        traceEdge(cellBelow, flowMatrix, buffer)
                    }

                else -> {}
            }
        }


        private fun layers(
                successors: Map<ObjectLocation, List<ObjectLocation>>,
                vertexMap: Map<ObjectLocation, VertexDescriptor>,
                predecessors: Map<ObjectLocation, List<ObjectLocation>>
        ): List<List<ObjectLocation>> {
            if (successors.isEmpty()) {
                return listOf()
            }

            val builder = mutableListOf<List<ObjectLocation>>()

            val open = mutableSetOf<ObjectLocation>()
            open.addAll(successors.keys)

            val layerBuilder = mutableListOf<ObjectLocation>()

            while (open.isNotEmpty()) {
                next_candidate@
                for (candidate in open) {
                    val candidatePredecessors = predecessors[candidate]
                            ?: listOf()

                    for (predecessor in candidatePredecessors) {
                        if (predecessor in open) {
                            continue@next_candidate
                        }
                    }

                    layerBuilder.add(candidate)
                }

                check(layerBuilder.isNotEmpty()) {
                    "Cycle detected: $open"
                }

                val nextLayer = sortedByMatrix(layerBuilder, vertexMap)

                builder.add(nextLayer)
                layerBuilder.clear()

                open.removeAll(nextLayer)
            }

            return builder
        }


        private fun predecessors(
                successors: Map<ObjectLocation, List<ObjectLocation>>
        ): Map<ObjectLocation, List<ObjectLocation>> {
            val predecessors = mutableMapOf<ObjectLocation, MutableList<ObjectLocation>>()

            for ((vertex, vertexSuccessors) in successors) {
                for (vertexSuccessor in vertexSuccessors) {
                    predecessors.getOrPut(vertexSuccessor) { mutableListOf() }

                    predecessors[vertexSuccessor]!!.add(vertex)
                }
            }

            return predecessors
        }


        @Suppress("MapGetWithNotNullAssertionOperator")
        private fun sortedByMatrix(
                vertexLocations: Iterable<ObjectLocation>,
                vertexMap: Map<ObjectLocation, VertexDescriptor>
        ): List<ObjectLocation> {
            return vertexLocations.sortedWith(Comparator { a, b ->
                val aInfo = vertexMap[a]!!
                val bInfo = vertexMap[b]!!

                CellDescriptor.byRowThenColumn.compare(aInfo, bInfo)
            })
        }
    }
}