package tech.kzen.auto.common.paradigm.dataflow.model.structure

import tech.kzen.auto.common.objects.document.graph.DataflowWiring
import tech.kzen.auto.common.objects.document.graph.GraphDocument
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellCoordinate
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.VertexDescriptor
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.collect.persistentListOf


// TODO: optimize via mutable builder
data class DataflowMatrix(
        val rows: List<List<CellDescriptor>>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = DataflowMatrix(listOf())


        fun ofGraphDocument(
                host: DocumentPath,
                graphStructure: GraphStructure
        ): DataflowMatrix {
            val documentNotation = graphStructure.graphNotation.documents.get(host)
                    ?: return empty

            val verticesNotation = verticesNotation(documentNotation)
            val edgesNotation = edgesNotation(documentNotation)

            return cellDescriptorLayers(
                    graphStructure,  verticesNotation, edgesNotation)
        }


        fun verticesNotation(
                documentNotation: DocumentNotation
        ): ListAttributeNotation {
            return documentNotation
                    .objects
                    .notations
                    .values[NotationConventions.mainObjectPath]
                    ?.attributes
                    ?.values
                    ?.get(GraphDocument.verticesAttributeName)
                    as? ListAttributeNotation
                    ?: ListAttributeNotation(persistentListOf())
        }


        fun edgesNotation(
                documentNotation: DocumentNotation
        ): ListAttributeNotation {
            return documentNotation
                    .objects
                    .notations
                    .values[NotationConventions.mainObjectPath]
                    ?.attributes
                    ?.values
                    ?.get(GraphDocument.edgesAttributeName)
                    as? ListAttributeNotation
                    ?: ListAttributeNotation(persistentListOf())
        }


        fun cellDescriptorLayers(
                graphStructure: GraphStructure,
                verticesNotation: ListAttributeNotation,
                edgesNotation: ListAttributeNotation
        ): DataflowMatrix {
            val vertexDescriptors = verticesNotation.values.withIndex().map {
                val vertexNotation = it.value as ScalarAttributeNotation
                val vertexReference = ObjectReference.parse(vertexNotation.value)
                val objectLocation = graphStructure.graphNotation.coalesce.locate(vertexReference)
                val objectNotation = graphStructure.graphNotation.coalesce[objectLocation]!!

                val inputNames = DataflowWiring.findInputs(objectLocation, graphStructure)

                VertexDescriptor.fromNotation(
                        it.index,
                        inputNames,
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
                if (cell.coordinate.column == columnIndex ||
                        cell is VertexDescriptor &&
                        columnIndex >= cell.coordinate.column &&
                        columnIndex < cell.coordinate.column + cell.inputNames.size) {
                    return cell
                }
            }

            return null
        }

        return null
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun traceVertexBackFrom(vertexLocation: ObjectLocation): Set<VertexDescriptor> {
        val vertexDescriptor = verticesByLocation[vertexLocation]
                ?: return setOf()

        val buffer = mutableSetOf<VertexDescriptor>()
        for (inputName in vertexDescriptor.inputNames) {
            val sourceVertex = traceVertexBackFrom(vertexDescriptor, inputName)

            if (sourceVertex != null) {
                buffer.add(sourceVertex)
            }
        }
        return buffer
    }


    fun traceVertexBackFrom(
            vertexDescriptor: VertexDescriptor,
            inputName: AttributeName
    ): VertexDescriptor? {
        val leftOffset = vertexDescriptor.inputNames.indexOf(inputName)

        val above = vertexDescriptor.coordinate.offset(-1, leftOffset)

        return when (val cellAbove = get(above)) {
            null ->
                null

            else ->
                traceVertexBackFrom(cellAbove)
        }
    }


    private fun traceVertexBackFrom(
            descriptor: CellDescriptor
    ): VertexDescriptor? {
        when (descriptor) {
            is VertexDescriptor ->
                return descriptor

            is EdgeDescriptor -> {
                if (descriptor.orientation.hasTop()) {
                    val coordinateAbove = descriptor.coordinate.offset(-1, 0)
                    val descriptorAbove = get(coordinateAbove)
                    if (descriptorAbove is VertexDescriptor ||
                            (descriptorAbove as? EdgeDescriptor)?.orientation?.hasBottom() == true) {
                        return traceVertexBackFrom(descriptorAbove)
                    }
                }

                if (descriptor.orientation.hasLeftIngress()) {
                    val coordinateLeft = descriptor.coordinate.offset(0, -1)
                    val edgeDescriptorLeft = get(coordinateLeft) as? EdgeDescriptor
                    if (edgeDescriptorLeft != null && edgeDescriptorLeft.orientation.hasRightEgress()) {
                        val vertexLeft = traceVertexBackFrom(edgeDescriptorLeft)
                        if (vertexLeft != null) {
                            return vertexLeft
                        }
                    }
                }

                if (descriptor.orientation.hasRightIngress()) {
                    val coordinateRight = descriptor.coordinate.offset(0, 1)
                    val edgeDescriptorRight = get(coordinateRight) as? EdgeDescriptor
                    if (edgeDescriptorRight != null && edgeDescriptorRight.orientation.hasLeftEgress()) {
                        val vertexRight = traceVertexBackFrom(edgeDescriptorRight)
                        if (vertexRight != null) {
                            return vertexRight
                        }
                    }
                }

                return null
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun traceEdgeBackFrom(
            vertexDescriptor: VertexDescriptor,
            offsetLeft: Int
    ): List<EdgeDescriptor> {
        val above = vertexDescriptor.coordinate.offset(-1, offsetLeft)

        val edgeDescriptorAbove = get(above) as? EdgeDescriptor
                ?: return listOf()

        if (! edgeDescriptorAbove.orientation.hasBottom()) {
            return listOf()
        }

        val edges = mutableListOf<EdgeDescriptor>()

        traceEdgeBackFrom(edgeDescriptorAbove, edges)

        return edges
    }


    private fun traceEdgeBackFrom(
            descriptor: EdgeDescriptor,
            buffer: MutableList<EdgeDescriptor>
    ) {
        buffer.add(descriptor)

        if (descriptor.orientation.hasTop()) {
            val coordinateAbove = descriptor.coordinate.offset(-1, 0)
            val edgeDescriptorAbove = get(coordinateAbove) as? EdgeDescriptor
            if (edgeDescriptorAbove != null && edgeDescriptorAbove.orientation.hasBottom()) {
                traceEdgeBackFrom(edgeDescriptorAbove, buffer)
            }
        }

        if (descriptor.orientation.hasLeftIngress()) {
            val coordinateLeft = descriptor.coordinate.offset(0, -1)
            val edgeDescriptorLeft = get(coordinateLeft) as? EdgeDescriptor
            if (edgeDescriptorLeft != null && edgeDescriptorLeft.orientation.hasRightEgress()) {
                traceEdgeBackFrom(edgeDescriptorLeft, buffer)
            }
        }

        if (descriptor.orientation.hasRightIngress()) {
            val coordinateRight = descriptor.coordinate.offset(0, 1)
            val edgeDescriptorRight = get(coordinateRight) as? EdgeDescriptor
            if (edgeDescriptorRight != null && edgeDescriptorRight.orientation.hasLeftEgress()) {
                traceEdgeBackFrom(edgeDescriptorRight, buffer)
            }
        }
    }
}