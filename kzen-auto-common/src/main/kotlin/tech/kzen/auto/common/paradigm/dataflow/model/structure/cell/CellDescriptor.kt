package tech.kzen.auto.common.paradigm.dataflow.model.structure.cell

import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.notation.model.MapAttributeNotation
import tech.kzen.lib.common.structure.notation.model.ObjectNotation


//---------------------------------------------------------------------------------------------------------------------
sealed class CellDescriptor {
    companion object {
        val byRowThenColumn: Comparator<CellDescriptor> =
                compareBy(CellCoordinate.byRowThenColumn) {
                    it.coordinate
                }
    }


    abstract val coordinate: CellCoordinate
}


//---------------------------------------------------------------------------------------------------------------------
data class EdgeDescriptor(
        val indexInEdges: Int,
        val orientation: EdgeOrientation,
        override val coordinate: CellCoordinate
): CellDescriptor() {
    companion object {
        const val orientationAttributeKey = "orientation"
        val orientationAttributeSegment = AttributeSegment.ofKey(orientationAttributeKey)


        fun fromNotation(
                indexInEdges: Int,
                attributeNotation: MapAttributeNotation
        ): EdgeDescriptor {
            val coordinate = CellCoordinate.fromAttributeNotation(attributeNotation)

            val orientation = attributeNotation.get(orientationAttributeSegment)
                    ?.asString()
                    ?.let { EdgeOrientation.valueOf(it) }
                    ?: throw IllegalArgumentException("Orientation missing: $attributeNotation")

            return EdgeDescriptor(
                    indexInEdges,
                    orientation,
                    coordinate)
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
data class VertexDescriptor(
        val indexInVertices: Int,
        val objectLocation: ObjectLocation,
        override val coordinate: CellCoordinate
): CellDescriptor() {
    companion object {
        fun fromNotation(
                indexInVertices: Int,
                objectLocation: ObjectLocation,
                objectNotation: ObjectNotation
        ): VertexDescriptor {
            val coordinate = CellCoordinate.fromObjectNotation(objectNotation)

            return VertexDescriptor(
                    indexInVertices,
                    objectLocation,
                    coordinate)
        }
    }
}