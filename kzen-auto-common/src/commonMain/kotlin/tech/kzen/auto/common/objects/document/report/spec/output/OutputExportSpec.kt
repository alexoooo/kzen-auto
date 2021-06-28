package tech.kzen.auto.common.objects.document.report.spec.output

import kotlinx.datetime.Instant
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.util.data.DataLocationGroup
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentName
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
        const val formatCsvName = "csv"
        const val formatTsvName = "tsv"
        val formatOptions = listOf(formatCsvName, formatTsvName)

        const val compressionNoneName = "none"
        const val compressionZipName = "zip"
        const val compressionGzName = "gz"
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


        private fun resolvePattern(
            pattern: String, reportName: DocumentName, group: DataLocationGroup, time: Instant, extension: String
        ): String {
            val timeFormat = FormatUtils.formatLocalDateTime(time)
                .replace("-", "")
                .replace(":", "")
                .replace(" ", "T")

            val sanitizedReportName = FormatUtils.sanitizeFilename(reportName.value)
            val sanitizedGroup = FormatUtils.sanitizeFilename(group.group ?: "")

            return pattern
                .replace("\${report}", sanitizedReportName)
                .replace("\${group}", sanitizedGroup)
                .replace("\${time}", timeFormat)
                .replace("\${extension}", extension)
                .replace(Regex("_+"), "_")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun resolvePath(reportName: DocumentName, group: DataLocationGroup, time: Instant): String {
        val extension =
            if (compression == compressionZipName) {
                "zip"
            }
            else {
                val outerExtension =
                    if (compression == compressionNoneName) {
                        ""
                    }
                    else {
                        ".$compression"
                    }

                format + outerExtension
            }

        return resolvePattern(pathPattern, reportName, group, time, extension)
    }


    fun resolveInnerFilename(reportName: DocumentName, group: DataLocationGroup, time: Instant): String {
        val outerExtension =
            if (compression == compressionNoneName ||
                    compression == compressionZipName
            ) {
                ""
            }
            else {
                ".$compression"
            }

        val extension = format + outerExtension

        val indexOfLastSeparator =
            if (pathPattern.contains('/')) {
                pathPattern.lastIndexOf('/')
            }
            else {
                pathPattern.lastIndexOf('\\')
            }

        val namePattern =
            if (indexOfLastSeparator == -1) {
                pathPattern
            }
            else {
                pathPattern.substring(indexOfLastSeparator + 1)
            }

        return resolvePattern(namePattern, reportName, group, time, extension)
    }
}