package tech.kzen.auto.common.paradigm.dataflow.model.structure

import tech.kzen.auto.common.objects.document.query.QueryDocument
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.VertexDescriptor
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.common.structure.notation.model.ListAttributeNotation
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation
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
                    ?: return empty

            return vertexInfoLayers(graphNotation,  verticesNotation)
        }


        fun verticesNotation(
                documentNotation: DocumentNotation
        ): ListAttributeNotation? {
            return documentNotation
                    .objects
                    .values[NotationConventions.mainObjectPath]
                    ?.attributes
                    ?.values
                    ?.get(QueryDocument.verticesAttributeName)
                    as? ListAttributeNotation
                    ?: ListAttributeNotation(persistentListOf())
        }


        fun vertexInfoLayers(
                graphNotation: GraphNotation,
                verticesNotation: ListAttributeNotation
        ): DataflowMatrix {
            val vertexInfos = verticesNotation.values.withIndex().map {
                val vertexReference = ObjectReference.parse((it.value as ScalarAttributeNotation).value)
                val objectLocation = graphNotation.coalesce.locate(vertexReference)
                val objectNotation = graphNotation.coalesce.get(objectLocation)!!
                VertexDescriptor.fromNotation(
                        it.index,
                        objectLocation,
                        objectNotation)
            }

            return ofUnorderedDescriptors(vertexInfos)
        }


        fun ofUnorderedDescriptors(
                unorderedInfos: List<VertexDescriptor>
        ): DataflowMatrix {
            val sortedByRowThenColumn = unorderedInfos.sortedWith(CellDescriptor.byRowThenColumn)

            val matrix = mutableListOf<List<VertexDescriptor>>()
            val row = mutableListOf<VertexDescriptor>()
            var previousRow = -1
            for (vertexDescriptor in sortedByRowThenColumn) {
                if (/*previousRow != -1 &&*/
                        previousRow != vertexDescriptor.coordinate.row &&
                        row.isNotEmpty()) {
//                    for (skipped in previousRow until vertexInfo.row) {
//                        matrix.add(listOf())
//                    }
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



    fun byLocation(): Map<ObjectLocation, VertexDescriptor> {
        val builder = mutableMapOf<ObjectLocation, VertexDescriptor>()

        for (row in rows) {
            for (vertexInfo in row) {
                builder[(vertexInfo as VertexDescriptor).objectLocation] = vertexInfo
            }
        }

        return builder
    }


    fun isEmpty(): Boolean {
        return rows.isEmpty()
    }


    fun get(rowIndex: Int, columnIndex: Int): VertexDescriptor? {
        for (row in rows) {
            if (row.first().coordinate.row != rowIndex) {
                continue
            }

            for (cell in row) {
                if (cell.coordinate.column != columnIndex) {
                    continue
                }
                return cell as VertexDescriptor
            }

            return null
        }

        return null
    }
}