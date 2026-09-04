package tech.kzen.auto.server.objects.datasource

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.api.DataSource
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.format.detection.NormalizedFormatHints
import tech.kzen.auto.common.data.model.DataDiagnostic
import tech.kzen.auto.common.data.model.DataManifest
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataResolveResult
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.data.format.SourceFormatResolutionBudget
import tech.kzen.auto.server.data.format.SourceFormatResolutionBudgetFactory
import tech.kzen.auto.server.objects.datasource.format.ConfiguredRecordFormatLookup
import tech.kzen.auto.server.objects.datasource.format.ConfiguredRecordFormatPreflight
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


@Reflect
class FileDataSource(
    private val directory: String,
    private val filter: String,
    files: List<Map<String, String>>,
    private val format: ConfiguredRecordFormat,
    private val groupPattern: String,
    private val missing: String,
    @Service private val fileListingAction: FileListingAction,
    @Service private val formatLookup: ConfiguredRecordFormatLookup = unavailableFormatLookup,
    @Service private val resolutionBudgetFactory: SourceFormatResolutionBudgetFactory =
        SourceFormatResolutionBudgetFactory()
): FileResolutionDataSource {
    companion object {
        const val missingFail = "fail"
        const val missingSkip = "skip"

        private const val groupAttribute = "group"
        private val unavailableFormatLookup = object: ConfiguredRecordFormatLookup {
            override suspend fun preflight(reference: String): ConfiguredRecordFormatPreflight {
                throw IllegalArgumentException("Configured format lookup is unavailable for $reference")
            }
        }
    }


    private val files = files.map(FileSelectionEntry::ofCollection)


    override suspend fun resolve(context: DataContext): DataResolveResult {
        require(missing == missingFail || missing == missingSkip) {
            "Unknown missing-file policy: $missing"
        }

        return resolutionBudgetFactory.create().withinDeadline {
            val sourceFormat = formatLookup.preflight(format)
            val selected = list(context)
            val diagnostics = mutableListOf<DataDiagnostic>()
            val regularFiles = validateSelection(selected, diagnostics)
            val prepared = prepareOverrides(regularFiles)
            val sourceBudget = this
            val resolved = coroutineScope {
                prepared.map { input ->
                    async {
                        resolveInput(context, input, sourceFormat, sourceBudget)
                    }
                }.awaitAll()
            }
            DataResolveResult(
                DataManifest(resolved.map(ResolvedInput::unit)),
                diagnostics,
                resolved.map { it.resolution.detail })
        }
    }


    override suspend fun resolveFile(
        context: DataContext,
        entry: FileSelectionEntry
    ): DataResolveResult {
        return resolutionBudgetFactory.create().withinDeadline {
            val sourceFormat = formatLookup.preflight(format)
            val selected = selectedFile(context, entry)
            require(selected.size == 1) {
                "Selected file is unavailable or ambiguous: ${entry.location.asString()}"
            }
            val diagnostics = mutableListOf<DataDiagnostic>()
            val regular = validateSelection(selected, diagnostics)
            require(regular.size == 1) { "Selected file is unavailable: ${entry.location.asString()}" }
            val prepared = prepareOverrides(listOf(entry to regular.single().second)).single()
            val resolved = resolveInput(context, prepared, sourceFormat, this)
            DataResolveResult(
                DataManifest(listOf(resolved.unit)),
                diagnostics,
                listOf(resolved.resolution.detail))
        }
    }


    private suspend fun selectedFile(
        context: DataContext,
        entry: FileSelectionEntry
    ): List<Pair<FileSelectionEntry?, DataLocationInfo>> {
        if (files.isNotEmpty()) {
            require(entry in files) {
                "The selected file row changed before resolution: ${entry.location.asString()}"
            }
            return listOf(entry to context.blocking {
                fileListingAction.fileInfoBlocking(entry.location)
            })
        }
        return list(context).filter { (_, info) -> info.path == entry.location }
    }


    override fun staticShape(role: DataRole?): tech.kzen.auto.common.data.schema.DataShape? {
        return if ((role == null || role == DataRole.main) && files.none { it.format != null }) {
            format.declaredShape()
        }
        else null
    }


    private suspend fun list(context: DataContext): List<Pair<FileSelectionEntry?, DataLocationInfo>> {
        return context.blocking {
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
                    .sortedBy { it.second.path.asString() }
            }
        }
    }


    private fun validateSelection(
        selected: List<Pair<FileSelectionEntry?, DataLocationInfo>>,
        diagnostics: MutableList<DataDiagnostic>
    ): List<Pair<FileSelectionEntry?, DataLocationInfo>> {
        val regularFiles = mutableListOf<Pair<FileSelectionEntry?, DataLocationInfo>>()
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
            regularFiles.add(entry to info)
        }
        return regularFiles
    }


    private suspend fun prepareOverrides(
        selected: List<Pair<FileSelectionEntry?, DataLocationInfo>>
    ): List<PreparedInput> {
        val preflights = mutableMapOf<String, ConfiguredRecordFormatPreflight>()
        return selected.map { (entry, info) ->
            val reference = entry?.format?.asString()
            val preflight = if (reference == null) {
                null
            }
            else {
                preflights[reference] ?: preflight(reference, info).also {
                    require(it.selectionKind == FormatSelectionKind.Explicit) {
                        "Per-file format override '$reference' for ${info.path.asString()} must be concrete"
                    }
                    preflights[reference] = it
                }
            }
            PreparedInput(entry, info, preflight)
        }
    }


    private suspend fun preflight(
        reference: String,
        info: DataLocationInfo
    ): ConfiguredRecordFormatPreflight {
        return try {
            formatLookup.preflight(reference)
        }
        catch (failure: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Unable to resolve per-file format override '$reference' for ${info.path.asString()}: " +
                    (failure.message ?: "invalid configured format"),
                failure)
        }
    }


    private suspend fun resolveInput(
        context: DataContext,
        input: PreparedInput,
        sourceFormat: ConfiguredRecordFormatPreflight?,
        sourceBudget: SourceFormatResolutionBudget
    ): ResolvedInput {
        val info = input.info
        val ref = DataRef.of(info)
        val expectedFingerprint = DataContentFingerprint.localOrNull(ref)
        val request = FormatResolutionRequest(
            context,
            ref,
            expectedFingerprint,
            hints(info.name),
            input.entry?.encoding?.asString(),
            sourceBudget)
        val resolution = input.formatOverride?.resolve(request)
            ?: sourceFormat?.resolve(request)
            ?: format.resolve(request)
        require(resolution.detail.ref == ref && resolution.detail.role == DataRole.main) {
            "Format resolution returned mismatched provenance for ${ref.display()}"
        }
        val part = DataPart(
            DataRole.main,
            ref,
            expectedFingerprint,
            resolution.resolvedRead)
        return ResolvedInput(DataUnit(groupAttributes(info.name), listOf(part)), resolution)
    }


    private fun hints(fileName: String): NormalizedFormatHints {
        val extension = fileName.substringAfterLast('.', "").takeIf(String::isNotEmpty)
        return NormalizedFormatHints.of(filenameExtension = extension)
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


    private data class PreparedInput(
        val entry: FileSelectionEntry?,
        val info: DataLocationInfo,
        val formatOverride: ConfiguredRecordFormatPreflight?
    )


    private data class ResolvedInput(
        val unit: DataUnit,
        val resolution: FormatResolutionResult
    )
}
