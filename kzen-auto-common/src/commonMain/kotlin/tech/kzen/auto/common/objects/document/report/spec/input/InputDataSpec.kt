package tech.kzen.auto.common.objects.document.report.spec.input

import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.persistentMapOf


data class InputDataSpec(
    val location: DataLocation,
    val processorDefinitionCoordinate: CommonPluginCoordinate
) {
    companion object {
        private const val locationKey = "location"
        private val locationAttributeSegment = AttributeSegment.ofKey(locationKey)

        private const val processorDefinitionCoordinateKey = "coordinate"
        private val processorDefinitionCoordinateAttributeSegment =
            AttributeSegment.ofKey(processorDefinitionCoordinateKey)


        fun coordinateNesting(index: Int): AttributeNesting {
            return InputSelectionSpec.locationNesting(index).push(processorDefinitionCoordinateAttributeSegment)
        }


        fun ofNotation(mapAttributeNotation: MapAttributeNotation): InputDataSpec {
            val location = DataLocation.of(mapAttributeNotation.values[locationAttributeSegment]!!.asString()!!)

            val processorDefinitionNameKey = mapAttributeNotation
                .values[processorDefinitionCoordinateAttributeSegment]!!
                .asString()!!

            return InputDataSpec(
                location, CommonPluginCoordinate.ofString(processorDefinitionNameKey))
        }


        fun ofCollection(collection: Map<String, String>): InputDataSpec {
            return InputDataSpec(
                DataLocation.of(collection[locationKey] as String),
                CommonPluginCoordinate.ofString(collection[processorDefinitionCoordinateKey] as String),
            )
        }
    }


    fun asNotation(): MapAttributeNotation {
        return MapAttributeNotation(persistentMapOf(
            locationAttributeSegment to ScalarAttributeNotation(location.asString()),
            processorDefinitionCoordinateAttributeSegment to
                    ScalarAttributeNotation(processorDefinitionCoordinate.asString())
        ))
    }


    fun asCollection(): Map<String, String> {
        return mapOf(
            locationKey to location.asString(),
            processorDefinitionCoordinateKey to processorDefinitionCoordinate.asString()
        )
    }
}