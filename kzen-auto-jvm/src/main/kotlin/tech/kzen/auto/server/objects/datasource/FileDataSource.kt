package tech.kzen.auto.server.objects.datasource

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.api.DataSource
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.model.DataDiagnostic
import tech.kzen.auto.common.data.model.DataManifest
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataResolveResult
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.objects.data.schema.DataSchemaDocument
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


@Reflect
class FileDataSource(
    private val directory: String,
    private val filter: String,
    files: List<Map<String, String>>,
    format: String,
    encoding: String,
    private val groupPattern: String,
    private val missing: String,
    @Service private val fileListingAction: FileListingAction,
    private val schema: DataSchemaDocument? = null
): DataSource {
    companion object {
        const val missingFail = "fail"
        const val missingSkip = "skip"

        private const val groupAttribute = "group"
    }


    private val files = files.map(FileSelectionEntry::ofCollection)
    private val defaultFormat = format.takeIf { it.isNotBlank() }?.let(CommonPluginCoordinate::ofString)
    private val defaultEncoding = encoding.takeIf { it.isNotBlank() }?.let(CommonDataEncodingSpec::ofString)


    override suspend fun resolve(context: DataContext): DataResolveResult {
        require(missing == missingFail || missing == missingSkip) {
            "Unknown missing-file policy: $missing"
        }

        val selected = context.blocking {
            if (files.isNotEmpty()) {
                files.map { it to fileListingAction.fileInfoBlocking(it.location) }
            }
            else if (directory.isBlank()) {
                emptyList()
            }
            else {
                fileListingAction
                    .scanInfoBlocking(DataLocation.of(directory), filter)
                    .map { null to it }
            }
        }.sortedBy { it.second.path.asString() }

        val units = mutableListOf<DataUnit>()
        val diagnostics = mutableListOf<DataDiagnostic>()
        for ((entry, info) in selected) {
            if (info.isMissing()) {
                if (missing == missingFail) {
                    throw IllegalStateException("Missing file: ${info.path.asString()}")
                }
                diagnostics.add(DataDiagnostic(DataDiagnostic.skipped, info.path.asString()))
                continue
            }
            if (info.directory) {
                throw IllegalStateException("Expected file, found directory: ${info.path.asString()}")
            }
            units.add(unit(info, entry))
        }

        return DataResolveResult(DataManifest(units), diagnostics)
    }


    override fun staticShape(role: DataRole?): tech.kzen.auto.common.data.schema.DataShape? {
        return if (role == null || role == DataRole.main) schema?.shape() else null
    }


    private fun unit(info: DataLocationInfo, entry: FileSelectionEntry?): DataUnit {
        val ref = DataRef.of(info)
        val part = DataPart(
            DataRole.main,
            ref,
            entry?.format ?: defaultFormat,
            entry?.encoding ?: defaultEncoding)
        return DataUnit(groupAttributes(info.name), listOf(part))
    }


    private fun groupAttributes(fileName: String): Map<String, String> {
        if (groupPattern.isBlank()) {
            return emptyMap()
        }

        val pattern = Regex(groupPattern)
        val match = pattern.find(fileName) ?: return emptyMap()
        val captures = captureGroups(groupPattern)
        val named = captures.filter { it.name != null }
        if (named.isNotEmpty()) {
            val attributes = linkedMapOf<String, String>()
            for (capture in named) {
                attributes[capture.name!!] = match.groups[capture.name]?.value ?: ""
            }
            return attributes
        }

        val unnamed = captures.filter { it.name == null }
        require(unnamed.size == 1) {
            "Group pattern must contain named captures or exactly one unnamed capture: $groupPattern"
        }
        return mapOf(groupAttribute to (match.groups[unnamed.single().index]?.value ?: ""))
    }


    private fun captureGroups(pattern: String): List<Capture> {
        val captures = mutableListOf<Capture>()
        var groupIndex = 0
        var escaped = false
        var characterClass = false
        var index = 0
        while (index < pattern.length) {
            val char = pattern[index]
            if (escaped) {
                escaped = false
                index++
                continue
            }
            if (char == '\\') {
                escaped = true
                index++
                continue
            }
            if (char == '[') {
                characterClass = true
                index++
                continue
            }
            if (char == ']' && characterClass) {
                characterClass = false
                index++
                continue
            }
            if (char != '(' || characterClass) {
                index++
                continue
            }

            if (pattern.getOrNull(index + 1) != '?') {
                groupIndex++
                captures.add(Capture(groupIndex, null))
                index++
                continue
            }

            if (pattern.getOrNull(index + 2) == '<') {
                val marker = pattern.getOrNull(index + 3)
                if (marker != '=' && marker != '!') {
                    val end = pattern.indexOf('>', index + 3)
                    require(end != -1) { "Unclosed named capture: $groupPattern" }
                    groupIndex++
                    captures.add(Capture(groupIndex, pattern.substring(index + 3, end)))
                }
            }
            index++
        }
        return captures
    }


    private data class Capture(
        val index: Int,
        val name: String?
    )
}
