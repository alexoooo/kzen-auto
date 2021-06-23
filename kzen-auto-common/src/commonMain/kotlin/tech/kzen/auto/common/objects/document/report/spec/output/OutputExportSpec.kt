package tech.kzen.auto.common.objects.document.report.spec.output

import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand


data class OutputExportSpec(
    val format: String,
    val compression: String,
    val pathPattern: String
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val formatOptions = listOf("csv", "tsv")
        val compressionOptions = listOf("none", "zip", "gz")


        private const val formatKey = "format"
        private val formatAttributePath = OutputSpec.exportAttributePath.nest(AttributeSegment.ofKey(formatKey))

        private const val compressionKey = "compression"
        private val compressionAttributePath = OutputSpec.exportAttributePath.nest(AttributeSegment.ofKey(compressionKey))

        private const val pathPatterKey = "path"
        private val pathAttributePath = OutputSpec.exportAttributePath.nest(AttributeSegment.ofKey(pathPatterKey))


        fun ofNotation(attributeNotation: MapAttributeNotation): OutputExportSpec {
            val format = attributeNotation.get(formatKey)?.asString()
                ?: throw IllegalArgumentException("missing '$formatKey'")

            val compression = attributeNotation.get(compressionKey)?.asString()
                ?: throw IllegalArgumentException("missing '$compressionKey'")

            val pathPattern = attributeNotation.get(pathPatterKey)?.asString()
                ?: throw IllegalArgumentException("missing '$pathPatterKey'")

            return OutputExportSpec(format, compression, pathPattern)
        }


        fun changeFormatCommand(mainLocation: ObjectLocation, format: String): NotationCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                formatAttributePath,
                ScalarAttributeNotation(format))
        }


        fun changeCompressionCommand(mainLocation: ObjectLocation, compression: String): NotationCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                compressionAttributePath,
                ScalarAttributeNotation(compression))
        }


        fun changePathCommand(mainLocation: ObjectLocation, path: String): NotationCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                pathAttributePath,
                ScalarAttributeNotation(path))
        }
    }
}