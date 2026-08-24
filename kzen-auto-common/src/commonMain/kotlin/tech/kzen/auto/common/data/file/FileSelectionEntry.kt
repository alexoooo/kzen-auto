package tech.kzen.auto.common.data.file

import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.platform.collect.toPersistentMap


data class FileSelectionEntry(
    val location: DataLocation,
    val format: CommonPluginCoordinate?,
    val encoding: CommonDataEncodingSpec?
): Digestible {
    companion object {
        const val locationKey = "location"
        const val formatKey = "format"
        const val encodingKey = "encoding"

        private val locationSegment = AttributeSegment.ofKey(locationKey)
        private val formatSegment = AttributeSegment.ofKey(formatKey)
        private val encodingSegment = AttributeSegment.ofKey(encodingKey)


        fun ofNotation(notation: MapAttributeNotation): FileSelectionEntry {
            val location = notation[locationSegment]?.asString()
                ?: throw IllegalArgumentException("Missing '$locationKey': $notation")
            return FileSelectionEntry(
                DataLocation.of(location),
                notation[formatSegment]?.asString()?.takeIf { it.isNotBlank() }
                    ?.let(CommonPluginCoordinate::ofString),
                notation[encodingSegment]?.asString()?.takeIf { it.isNotBlank() }
                    ?.let(CommonDataEncodingSpec::ofString))
        }


        fun ofCollection(collection: Map<String, String>): FileSelectionEntry {
            val location = collection[locationKey]
                ?: throw IllegalArgumentException("Missing '$locationKey': $collection")
            return FileSelectionEntry(
                DataLocation.of(location),
                collection[formatKey]?.takeIf { it.isNotBlank() }
                    ?.let(CommonPluginCoordinate::ofString),
                collection[encodingKey]?.takeIf { it.isNotBlank() }
                    ?.let(CommonDataEncodingSpec::ofString))
        }
    }


    fun asNotation(): MapAttributeNotation {
        val notation = mutableMapOf(
            locationSegment to ScalarAttributeNotation(location.asString()))
        format?.let { notation[formatSegment] = ScalarAttributeNotation(it.asString()) }
        encoding?.let { notation[encodingSegment] = ScalarAttributeNotation(it.asString()) }
        return MapAttributeNotation(notation.toPersistentMap())
    }


    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(location)
        sink.addUtf8Nullable(format?.asString())
        sink.addUtf8Nullable(encoding?.asString())
    }
}
