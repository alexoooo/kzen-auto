package tech.kzen.auto.common.objects.document.report.spec.output

import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.util.data.DataLocationGroup
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import kotlin.time.Instant


data class OutputExportSpec(
    val format: String,
    val compression: String,
    val pathPattern: String
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // The Job ExportWriterWorker carries its export config as a top-level `export` attribute (defined by
        // [Definer]); the Report document instead nests it under `output.export` (defined by OutputSpec.Definer).
        val exportAttributeName = AttributeName("export")

        const val formatCsvName = "csv"
        const val formatTsvName = "tsv"
        val formatOptions = listOf(formatCsvName, formatTsvName)
        val formatOptionLabels = formatOptions.associateWith { it }

        const val compressionNoneName = "none"
        const val compressionZipName = "zip"
        const val compressionGzName = "gz"
        val compressionOptions = listOf("none", "zip", "gz")
        val compressionOptionLabels = compressionOptions.associateWith { it }


        private const val formatKey = "format"
        val formatAttributePath = OutputSpec.exportAttributePath.nest(AttributeSegment.ofKey(formatKey))

        private const val compressionKey = "compression"
        val compressionAttributePath = OutputSpec.exportAttributePath.nest(AttributeSegment.ofKey(compressionKey))

        private const val pathPatterKey = "path"
        val pathAttributePath = OutputSpec.exportAttributePath.nest(AttributeSegment.ofKey(pathPatterKey))


        // Attribute paths for the Job ExportWriterWorker, whose `export` map is a TOP-LEVEL attribute (defined by
        // [Definer]) rather than nested under the Report document's `output.export` (the paths above). The Job
        // ExportSpecEditor points the generic Select/Text attribute editors at these; UpdateInAttributeCommand
        // coalesces the merged (archetype-default) `export` map into the instance notation before the nested
        // write, so editing a single key works even on a freshly palette-inserted worker that only inherits it.
        private val standaloneExportAttributePath = AttributePath.ofName(exportAttributeName)
        val standaloneFormatAttributePath = standaloneExportAttributePath.nest(AttributeSegment.ofKey(formatKey))
        val standaloneCompressionAttributePath =
            standaloneExportAttributePath.nest(AttributeSegment.ofKey(compressionKey))
        val standalonePathAttributePath = standaloneExportAttributePath.nest(AttributeSegment.ofKey(pathPatterKey))


        fun ofNotation(attributeNotation: MapAttributeNotation): OutputExportSpec {
            val format = attributeNotation.get(formatKey)?.asString()
                ?: throw IllegalArgumentException("missing '$formatKey'")

            val compression = attributeNotation.get(compressionKey)?.asString()
                ?: throw IllegalArgumentException("missing '$compressionKey'")

            val pathPattern = attributeNotation.get(pathPatterKey)?.asString()
                ?: throw IllegalArgumentException("missing '$pathPatterKey'")

            return OutputExportSpec(format, compression, pathPattern)
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
                .replace($$"${report}", sanitizedReportName)
                .replace($$"${group}", sanitizedGroup)
                .replace($$"${time}", timeFormat)
                .replace($$"${extension}", extension)
                .replace(Regex("_+"), "_")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Defines a standalone `export` attribute (a map with format / compression / path) directly into an
    // OutputExportSpec, so the Job ExportWriterWorker can carry its export config without the surrounding
    // OutputSpec (type / explore / work) the Report document uses. Mirrors PivotSpec.Definer; mergeAttribute (not
    // firstAttribute) so an instance overriding just one key (e.g. compression) inherits the archetype defaults.
    @Reflect
    object Definer: AttributeDefiner {
        override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
        ): AttributeDefinitionAttempt {
            check(attributeName == exportAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val attributeNotation = graphStructure
                .graphNotation
                .mergeAttribute(objectLocation, exportAttributeName) as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'$exportAttributeName' attribute notation not found: $objectLocation - $attributeName")

            return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(ofNotation(attributeNotation)))
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