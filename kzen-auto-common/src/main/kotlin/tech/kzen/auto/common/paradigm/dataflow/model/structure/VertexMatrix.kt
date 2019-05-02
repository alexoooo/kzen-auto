package tech.kzen.auto.common.paradigm.dataflow.model.structure

import tech.kzen.auto.common.objects.document.query.QueryDocument
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
data class VertexMatrix(
        val rows: List<List<VertexInfo>>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = VertexMatrix(listOf())


        fun ofQueryDocument(
                host: DocumentPath,
                graphNotation: GraphNotation
        ): VertexMatrix {
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
        ): VertexMatrix {
            val vertexInfos = verticesNotation.values.withIndex().map {
                val vertexReference = ObjectReference.parse((it.value as ScalarAttributeNotation).value)
                val objectLocation = graphNotation.coalesce.locate(vertexReference)
                val objectNotation = graphNotation.coalesce.get(objectLocation)!!
                VertexInfo.fromDataflowNotation(
                        it.index,
                        objectLocation,
                        objectNotation)
            }

            return ofUnorderedInfos(vertexInfos)
        }


        fun ofUnorderedInfos(unorderedInfos: List<VertexInfo>): VertexMatrix {
            val sortedByRowThenColumn = unorderedInfos.sortedWith(VertexInfo.byRowThenColumn)

            val matrix = mutableListOf<List<VertexInfo>>()
            val row = mutableListOf<VertexInfo>()
            var previousRow = -1
            for (vertexInfo in sortedByRowThenColumn) {
                if (/*previousRow != -1 &&*/
                        previousRow != vertexInfo.row &&
                        row.isNotEmpty()) {
//                    for (skipped in previousRow until vertexInfo.row) {
//                        matrix.add(listOf())
//                    }
                    matrix.add(row.toList())
                    row.clear()
                }
                row.add(vertexInfo)
                previousRow = vertexInfo.row
            }
            if (row.isNotEmpty()) {
                matrix.add(row)
            }
            return VertexMatrix(matrix)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    val usedRows: Int = rows
            .lastOrNull()
            ?.first()
            ?.row
            ?.let { it + 1 }
            ?: 0


    val usedColumns: Int = rows
            .map { it.last().column }
            .max()
            ?.let { it + 1 }
            ?: 0



    fun byLocation(): Map<ObjectLocation, VertexInfo> {
        val builder = mutableMapOf<ObjectLocation, VertexInfo>()

        for (row in rows) {
            for (vertexInfo in row) {
                builder[vertexInfo.objectLocation] = vertexInfo
            }
        }

        return builder
    }


    fun isEmpty(): Boolean {
        return rows.isEmpty()
    }


    fun get(rowIndex: Int, columnIndex: Int): VertexInfo? {
        for (row in rows) {
            if (row.first().row != rowIndex) {
                continue
            }

            for (cell in row) {
                if (cell.column != columnIndex) {
                    continue
                }
                return cell
            }

            return null
        }

        return null
    }
}