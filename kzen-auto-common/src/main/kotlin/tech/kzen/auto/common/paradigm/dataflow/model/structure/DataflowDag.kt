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
        fun of(vertexMatrix: DataflowMatrix): DataflowDag {
            val vertexMap = vertexMatrix.verticesByLocation()
            val successors = successors(vertexMatrix, vertexMap)
            val predecessors = predecessors(successors)
            val layers = layers(successors, vertexMap, predecessors)
            return DataflowDag(successors, predecessors, layers)
        }


        private fun successors(
                vertexMatrix: DataflowMatrix,
                vertexMap: Map<ObjectLocation, VertexDescriptor>
        ): Map<ObjectLocation, List<ObjectLocation>> {
            val builder = mutableMapOf<ObjectLocation, List<ObjectLocation>>()

            for (vertexInfo in vertexMap.values) {
                val successorBuilder = mutableListOf<ObjectLocation>()

                for (successor in vertexSuccessors(vertexInfo, vertexMatrix)) {
                    successorBuilder.add(successor)
                }

                builder[vertexInfo.objectLocation] = successorBuilder.toPersistentList()
            }

            return builder
        }


        private fun vertexSuccessors(
                vertexDescriptor: VertexDescriptor,
                vertexMatrix: DataflowMatrix
        ): List<ObjectLocation> {
            @Suppress("MoveVariableDeclarationIntoWhen")
            val vertexBelow = vertexMatrix.get(
                    vertexDescriptor.coordinate.row + 1,
                    vertexDescriptor.coordinate.column
            ) ?: return listOf()

            return when (vertexBelow) {
                is EdgeDescriptor ->
                    listOf()

                is VertexDescriptor ->
                    listOf(vertexBelow.objectLocation)
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