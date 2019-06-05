package tech.kzen.auto.common.paradigm.dataflow.model.structure

import tech.kzen.auto.common.objects.document.query.QueryDocument
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellCoordinate
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.VertexDescriptor
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.model.*
import tech.kzen.lib.platform.collect.persistentListOf


// TODO: optimize via mutable builder
data class DataflowMatrix(
        val rows: List<List<CellDescriptor>>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = DataflowMatrix(listOf())


        fun ofQueryDocument(
                host: DocumentPath,
                graphNotation: GraphNotation
        ): DataflowMatrix {
            val documentNotation = graphNotation.documents.get(host)
                    ?: return empty

            val verticesNotation = verticesNotation(documentNotation)
            val edgesNotation = edgesNotation(documentNotation)

            return cellDescriptorLayers(
                    graphNotation,  verticesNotation, edgesNotation)
        }


        fun verticesNotation(
                documentNotation: DocumentNotation
        ): ListAttributeNotation {
            return documentNotation
                    .objects
                    .values[NotationConventions.mainObjectPath]
                    ?.attributes
                    ?.values
                    ?.get(QueryDocument.verticesAttributeName)
                    as? ListAttributeNotation
                    ?: ListAttributeNotation(persistentListOf())
        }


        fun edgesNotation(
                documentNotation: DocumentNotation
        ): ListAttributeNotation {
            return documentNotation
                    .objects
                    .values[NotationConventions.mainObjectPath]
                    ?.attributes
                    ?.values
                    ?.get(QueryDocument.edgesAttributeName)
                    as? ListAttributeNotation
                    ?: ListAttributeNotation(persistentListOf())
        }


        fun cellDescriptorLayers(
                graphNotation: GraphNotation,
                verticesNotation: ListAttributeNotation,
                edgesNotation: ListAttributeNotation
        ): DataflowMatrix {
            val vertexDescriptors = verticesNotation.values.withIndex().map {
                val vertexNotation = it.value as ScalarAttributeNotation
                val vertexReference = ObjectReference.parse(vertexNotation.value)
                val objectLocation = graphNotation.coalesce.locate(vertexReference)
                val objectNotation = graphNotation.coalesce[objectLocation]!!
                VertexDescriptor.fromNotation(
                        it.index,
                        objectLocation,
                        objectNotation)
            }

            val edgeDescriptors = edgesNotation.values.withIndex().map {
                @Suppress("CAST_NEVER_SUCCEEDS")
                val edgeNotation = it.value as MapAttributeNotation

                EdgeDescriptor.fromNotation(
                        it.index,
                        edgeNotation)
            }

            return ofUnorderedDescriptors(vertexDescriptors, edgeDescriptors)
        }


        private fun ofUnorderedDescriptors(
                vertexDescriptors: List<VertexDescriptor>,
                edgeDescriptors: List<EdgeDescriptor>
        ): DataflowMatrix {
            val sortedByRowThenColumn = mutableListOf<CellDescriptor>()
            sortedByRowThenColumn.addAll(vertexDescriptors)
            sortedByRowThenColumn.addAll(edgeDescriptors)
            sortedByRowThenColumn.sortWith(CellDescriptor.byRowThenColumn)

            val matrix = mutableListOf<List<CellDescriptor>>()
            val row = mutableListOf<CellDescriptor>()
            var previousRow = -1
            for (vertexDescriptor in sortedByRowThenColumn) {
                if (previousRow != vertexDescriptor.coordinate.row &&
                        row.isNotEmpty()) {
                    matrix.add(row.toList())
                    row.clear()
                }
                row.add(vertexDescriptor)
                previousRow = vertexDescriptor.coordinate.row
            }
            if (row.isNotEmpty()) {
                matrix.add(row)
            }
            return DataflowMatrix(matrix)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    val usedRows: Int = rows
            .lastOrNull()
            ?.first()
            ?.coordinate
            ?.row
            ?.let { it + 1 }
            ?: 0


    val usedColumns: Int = rows
            .map { it.last().coordinate.column }
            .max()
            ?.let { it + 1 }
            ?: 0


    val verticesByLocation: Map<ObjectLocation, VertexDescriptor> by lazy {
        val builder = mutableMapOf<ObjectLocation, VertexDescriptor>()

        for (row in rows) {
            for (vertexInfo in row) {
                if (vertexInfo !is VertexDescriptor) {
                    continue
                }
                builder[vertexInfo.objectLocation] = vertexInfo
            }
        }

        builder
    }


    fun isEmpty(): Boolean {
        return rows.isEmpty()
    }


    fun get(coordinate: CellCoordinate): CellDescriptor? {
        return get(coordinate.row, coordinate.column)
    }


    fun get(rowIndex: Int, columnIndex: Int): CellDescriptor? {
        for (row in rows) {
            if (row.first().coordinate.row != rowIndex) {
                continue
            }

            for (cell in row) {
                if (cell.coordinate.column != columnIndex) {
                    continue
                }
                return cell
            }

            return null
        }

        return null
    }


    fun leadingEdges(
            coordinate: CellCoordinate
    ): List<EdgeDescriptor> {
        if (get(coordinate) !is VertexDescriptor) {
            return listOf()
        }

        val edgeDescriptorAbove = get(coordinate.offset(-1, 0)) as? EdgeDescriptor
                ?: return listOf()

        if (! edgeDescriptorAbove.orientation.hasBottom()) {
            return listOf()
        }

        val edges = mutableListOf<EdgeDescriptor>()

        traceBackFrom(edgeDescriptorAbove, edges)

        return edges
    }


    private fun traceBackFrom(
            descriptor: EdgeDescriptor,
            buffer: MutableList<EdgeDescriptor>
    ) {
        buffer.add(descriptor)

        if (descriptor.orientation.hasTop()) {
            val coordinateAbove = descriptor.coordinate.offset(-1, 0)
            val edgeDescriptorAbove = get(coordinateAbove) as? EdgeDescriptor
            if (edgeDescriptorAbove != null && edgeDescriptorAbove.orientation.hasBottom()) {
                traceBackFrom(edgeDescriptorAbove, buffer)
            }
        }

        if (descriptor.orientation.hasLeftIngress()) {
            val coordinateLeft = descriptor.coordinate.offset(0, -1)
            val edgeDescriptorLeft = get(coordinateLeft) as? EdgeDescriptor
            if (edgeDescriptorLeft != null && edgeDescriptorLeft.orientation.hasRightEgress()) {
                traceBackFrom(edgeDescriptorLeft, buffer)
            }
        }
    }
}