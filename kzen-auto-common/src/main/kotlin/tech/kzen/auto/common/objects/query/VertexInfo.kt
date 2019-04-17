package tech.kzen.auto.common.objects.query

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.notation.model.ObjectNotation


data class VertexInfo(
        val indexInVertices: Int,
        val objectLocation: ObjectLocation,
        val row: Int,
        val column: Int
) {
    companion object {
        val rowAttributeName = AttributeName("row")
        val columnAttributeName = AttributeName("column")

        val byRowThenColumn: Comparator<VertexInfo> = compareBy(
                { it.row },
                { it.column }
        )


        fun fromDataflowNotation(
                indexInVertices: Int,
                objectLocation: ObjectLocation,
                objectNotation: ObjectNotation
        ): VertexInfo {
            val rows = objectNotation.attributes.values[rowAttributeName]?.asString()?.toInt()
                    ?: throw IllegalArgumentException("$rowAttributeName expected: $objectNotation")

            val columns = objectNotation.attributes.values[columnAttributeName]?.asString()?.toInt()
                    ?: throw IllegalArgumentException("$columnAttributeName expected: $objectNotation")

            return VertexInfo(
                    indexInVertices,
                    objectLocation,
                    rows,
                    columns)
        }
    }
}