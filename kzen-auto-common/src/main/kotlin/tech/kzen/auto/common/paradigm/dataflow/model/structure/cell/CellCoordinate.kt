package tech.kzen.auto.common.paradigm.dataflow.model.structure.cell

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation


data class CellCoordinate(
        val row: Int,
        val column: Int
) {
    companion object {
        val rowAttributeKey = "row"
        val rowAttributeName = AttributeName(rowAttributeKey)
        val rowAttributeSegment = AttributeSegment.ofKey(rowAttributeKey)

        val columnAttributeKey = "column"
        val columnAttributeName = AttributeName(columnAttributeKey)
        val columnAttributeSegment = AttributeSegment.ofKey(columnAttributeKey)


        val byRowThenColumn: Comparator<CellCoordinate> = compareBy(
                { it.row },
                { it.column }
        )


        fun fromObjectNotation(
                objectNotation: ObjectNotation
        ): CellCoordinate {
            val rows = objectNotation.attributes.values[rowAttributeName]?.asString()?.toInt()
                    ?: throw IllegalArgumentException("$rowAttributeName expected: $objectNotation")

            val columns = objectNotation.attributes.values[columnAttributeName]?.asString()?.toInt()
                    ?: throw IllegalArgumentException("$columnAttributeName expected: $objectNotation")

            return CellCoordinate(
                    rows, columns)
        }


        fun fromAttributeNotation(
                attributeNotation: MapAttributeNotation
        ): CellCoordinate {
            val rows = attributeNotation.get(rowAttributeSegment)?.asString()?.toInt()
                    ?: throw IllegalArgumentException("$rowAttributeSegment expected: $attributeNotation")

            val columns = attributeNotation.get(columnAttributeSegment)?.asString()?.toInt()
                    ?: throw IllegalArgumentException("$columnAttributeSegment expected: $attributeNotation")

            return CellCoordinate(
                    rows, columns)
        }
    }


    fun offset(edgeDirection: EdgeDirection): CellCoordinate {
        return offset(edgeDirection.rowOffset, edgeDirection.columnOffset)
    }

    fun offset(rows: Int, columns: Int): CellCoordinate {
        return CellCoordinate(row + rows, column + columns)
    }
}