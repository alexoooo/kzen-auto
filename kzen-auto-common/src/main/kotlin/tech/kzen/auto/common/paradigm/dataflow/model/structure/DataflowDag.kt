package tech.kzen.auto.common.paradigm.dataflow.model.structure

import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.VertexDescriptor
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.platform.collect.toPersistentList


data class DataflowDag(
        val successors: Map<ObjectLocation, List<ObjectLocation>>,
        val predecessors: Map<ObjectLocation, List<ObjectLocation>>,
        val layers: List<List<ObjectLocation>>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun of(dataflowMatrix: DataflowMatrix): DataflowDag {
            val vertexMap = dataflowMatrix.verticesByLocation
            val successors = successors(dataflowMatrix, vertexMap)
            val predecessors = predecessors(successors)

//            val layers = dataflowMatrix
//                    .rows
//                    .map { row -> row
//                            .mapNotNull { it as? VertexDescriptor }
//                            .map { it.objectLocation }
//                    }

            val layers = layers(successors, vertexMap, predecessors)

            return DataflowDag(successors, predecessors, layers)
        }


        private fun successors(
                dataflowMatrix: DataflowMatrix,
                vertexMap: Map<ObjectLocation, VertexDescriptor>
        ): Map<ObjectLocation, List<ObjectLocation>> {
            val builder = mutableMapOf<ObjectLocation, List<ObjectLocation>>()

            for (vertexInfo in vertexMap.values) {
                val successorBuilder = mutableListOf<ObjectLocation>()

                for (successor in vertexSuccessors(vertexInfo, dataflowMatrix)) {
                    successorBuilder.add(successor)
                }

                builder[vertexInfo.objectLocation] = successorBuilder.toPersistentList()
            }

            return builder
        }


        private fun vertexSuccessors(
                vertexDescriptor: VertexDescriptor,
                dataflowMatrix: DataflowMatrix
        ): List<ObjectLocation> {
            @Suppress("MoveVariableDeclarationIntoWhen")
            val cellBelow = findCellBelow(
                    vertexDescriptor, dataflowMatrix
            ) ?: return listOf()

            return when (cellBelow) {
                is VertexDescriptor ->
                    listOf(cellBelow.objectLocation)

                is EdgeDescriptor ->
                    traceEdge(cellBelow, dataflowMatrix)
            }
        }


        private fun traceEdge(
                edgeDescriptor: EdgeDescriptor,
                dataflowMatrix: DataflowMatrix
        ): List<ObjectLocation> {
            if (! edgeDescriptor.orientation.hasTop()) {
                return listOf()
            }

            val buffer = mutableListOf<ObjectLocation>()
            traceEdge(edgeDescriptor, dataflowMatrix, buffer)
            return buffer
        }


        private fun traceEdge(
                edgeDescriptor: EdgeDescriptor,
                dataflowMatrix: DataflowMatrix,
                buffer: MutableList<ObjectLocation>
        ) {
            if (edgeDescriptor.orientation.hasBottom()) {
                traceCellBelow(edgeDescriptor, dataflowMatrix, buffer)
            }

            if (edgeDescriptor.orientation.hasRightEgress()) {
                @Suppress("MoveVariableDeclarationIntoWhen")
                val cellRight = dataflowMatrix.get(
                        edgeDescriptor.coordinate.row,
                        edgeDescriptor.coordinate.column + 1)

                when (cellRight) {
                    is EdgeDescriptor ->
                        if (cellRight.orientation.hasLeftIngress()) {
                            traceEdge(cellRight, dataflowMatrix, buffer)
                        }
                }
            }
        }

        private fun findCellBelow(
                cellDescriptor: CellDescriptor,
                dataflowMatrix: DataflowMatrix
        ): CellDescriptor? {
            @Suppress("MoveVariableDeclarationIntoWhen")
            val cellBelow = dataflowMatrix.get(
                    cellDescriptor.coordinate.row + 1,
                    cellDescriptor.coordinate.column)

            when (cellBelow) {
                is VertexDescriptor ->
                    return cellBelow

                is EdgeDescriptor ->
                    return cellBelow

                null -> {
                    for (i in cellDescriptor.coordinate.column - 1 downTo 0) {
                        val possibleMultiCell = dataflowMatrix.get(
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
                dataflowMatrix: DataflowMatrix,
                buffer: MutableList<ObjectLocation>
        ) {
            @Suppress("MoveVariableDeclarationIntoWhen")
            val cellBelow = findCellBelow(edgeDescriptor, dataflowMatrix)

            when (cellBelow) {
                is VertexDescriptor ->
                    buffer.add(cellBelow.objectLocation)

                is EdgeDescriptor ->
                    if (cellBelow.orientation.hasTop()) {
                        traceEdge(cellBelow, dataflowMatrix, buffer)
                    }
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