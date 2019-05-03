package tech.kzen.auto.common.paradigm.dataflow.model.structure

import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.platform.collect.toPersistentList


data class DataflowDag(
        val successors: Map<ObjectLocation, List<ObjectLocation>>,
        val layers: List<List<ObjectLocation>>,
        val predecessors: Map<ObjectLocation, List<ObjectLocation>>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun of(vertexMatrix: VertexMatrix): DataflowDag {
            val vertexMap = vertexMatrix.byLocation()
            val successors = successors(vertexMatrix, vertexMap)
            val predecessors = predecessors(successors)
            val layers = layers(successors, vertexMap, predecessors)
            return DataflowDag(successors, layers, predecessors)
        }


        private fun successors(
                vertexMatrix: VertexMatrix,
                vertexMap: Map<ObjectLocation, VertexInfo>
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
                vertexInfo: VertexInfo,
                vertexMatrix: VertexMatrix
        ): List<ObjectLocation> {
            val vertexBelow = vertexMatrix.get(vertexInfo.row + 1, vertexInfo.column)

            return if (vertexBelow == null) {
                listOf()
            } else {
                // TODO: take into account edges (pipes), in order

                listOf(vertexBelow.objectLocation)
            }
        }


        private fun layers(
                successors: Map<ObjectLocation, List<ObjectLocation>>,
                vertexMap: Map<ObjectLocation, VertexInfo>,
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
                vertexMap: Map<ObjectLocation, VertexInfo>
        ): List<ObjectLocation> {
            return vertexLocations.sortedWith(Comparator { a, b ->
                val aInfo = vertexMap[a]!!
                val bInfo = vertexMap[b]!!

                if (aInfo.row != bInfo.row) {
                    bInfo.row.compareTo(bInfo.row)
                }
                else {
                    bInfo.column.compareTo(bInfo.column)
                }
            })
        }
    }
}