package tech.kzen.auto.server.objects.datasource

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.api.DataSource
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.format.FormatSelectionKind
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
import tech.kzen.auto.server.objects.datasource.format.ConfiguredRecordFormatLookup
import tech.kzen.auto.server.objects.datasource.format.ConfiguredRecordFormatPreflight
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


@Reflect
class FileDataSource(
    files: List<Map<String, String>>,
    private val format: ConfiguredRecordFormat,
    private val missing: String,
    @Service private val fileListingAction: FileListingAction,
    @Service private val formatLookup: ConfiguredRecordFormatLookup = unavailableFormatLookup,
    // Absent when a Worker builds the source in code: its format then has no graph coordinate
    selfLocation: ObjectLocation? = null
): FileResolutionDataSource {
    companion object {
        const val missingFail = "fail"
        const val missingSkip = "skip"
        private val unavailableFormatLookup = object: ConfiguredRecordFormatLookup {
            override suspend fun preflight(reference: String): ConfiguredRecordFormatPreflight {
                throw IllegalArgumentException("Configured format lookup is unavailable for $reference")
            }
        }
    }


    private val files = files.map(FileSelectionEntry::ofCollection)

    // Where [format] came from: the reference a resolution reports is the one this attribute names
    private val formatAttribute = selfLocation?.let {
        AttributeLocation(it, ConfiguredRecordFormatLookup.formatAttributePath)
    }


    override suspend fun resolve(context: DataContext): DataResolveResult {
        require(missing == missingFail || missing == missingSkip) {
            "Unknown missing-file policy: $missing"
        }

        val sourceFormat = formatAttribute?.let { formatLookup.preflight(format, it) }
        val selected = list(context)
        val diagnostics = mutableListOf<DataDiagnostic>()
        val regularFiles = validateSelection(selected, diagnostics)
        val prepared = prepareOverrides(regularFiles)
        val resolved = prepared.map { input ->
            resolveInput(context, input, sourceFormat)
        }
        return DataResolveResult(
            DataManifest(resolved.map(ResolvedInput::unit)),
            diagnostics,
            resolved.map { it.resolution.detail })
    }


    override suspend fun resolveFile(
        context: DataContext,
        entry: FileSelectionEntry
    ): DataResolveResult {
        require(files.isNotEmpty()) {
            "Selected file is not in this source's selection: ${entry.location.asString()}"
        }
        require(entry in files) {
            "The selected file row changed before resolution: ${entry.location.asString()}"
        }
        val sourceFormat = formatAttribute?.let { formatLookup.preflight(format, it) }
        val selected = entry to context.blocking { fileListingAction.fileInfoBlocking(entry.location) }
        val diagnostics = mutableListOf<DataDiagnostic>()
        val regular = validateSelection(listOf(selected), diagnostics)
        require(regular.size == 1) { "Selected file is unavailable: ${entry.location.asString()}" }
        val prepared = prepareOverrides(regular).single()
        val resolved = resolveInput(context, prepared, sourceFormat)
        return DataResolveResult(
            DataManifest(listOf(resolved.unit)),
            diagnostics,
            listOf(resolved.resolution.detail))
    }


    /**
     * The selected files, without reading or resolving a format: `File` selects, `Parse` reads. Missing files
     * follow [missing]; a directory fails.
     */
    suspend fun select(context: DataContext): List<SelectedFile> {
        require(missing == missingFail || missing == missingSkip) {
            "Unknown missing-file policy: $missing"
        }
        val regularFiles = validateSelection(list(context), mutableListOf())
        return regularFiles.map { (entry, info) -> SelectedFile(info, entry) }
    }


    /**
     * One file of [select]: its listing and the selection row naming it, whose location is as written rather
     * than resolved like [DataLocationInfo.path].
     */
    class SelectedFile(
        val info: DataLocationInfo,
        val entry: FileSelectionEntry
    )


    override fun staticShape(
        role: DataRole?,
        configuredFormats: Lazy<List<ConfiguredRecordFormat>>
    ): tech.kzen.auto.common.data.schema.DataShape? {
        if ((role != null && role != DataRole.main) || files.any { it.format != null }) {
            return null
        }
        if (files.isEmpty()) {
            return format.declaredShape()
        }
        return files
            .map { format.declaredShape(it.location.fileName(), configuredFormats) }
            .distinct()
            .singleOrNull()
    }


    private suspend fun list(context: DataContext): List<Pair<FileSelectionEntry, DataLocationInfo>> {
        return context.blocking {
            files.map { it to fileListingAction.fileInfoBlocking(it.location) }
        }
    }


    private fun validateSelection(
        selected: List<Pair<FileSelectionEntry, DataLocationInfo>>,
        diagnostics: MutableList<DataDiagnostic>
    ): List<Pair<FileSelectionEntry, DataLocationInfo>> {
        val regularFiles = mutableListOf<Pair<FileSelectionEntry, DataLocationInfo>>()
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
        selected: List<Pair<FileSelectionEntry, DataLocationInfo>>
    ): List<PreparedInput> {
        val preflights = mutableMapOf<String, ConfiguredRecordFormatPreflight>()
        return selected.map { (entry, info) ->
            val reference = entry.format?.asString()
            val preflight = if (reference == null) {
                null
            }
            else {
                preflights[reference] ?: preflight(reference, info).also {
                    require(it.selectionKind == FormatSelectionKind.Explicit) {
                        "Per-file format override '$reference' for ${info.path.asString()} must be concrete"
                    }
                    require(it.format.readsContent) {
                        "Per-file format override '$reference' for ${info.path.asString()} must read the file: " +
                            "${it.format.title} applies to the whole source"
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
        sourceFormat: ConfiguredRecordFormatPreflight?
    ): ResolvedInput {
        val info = input.info
        val ref = DataRef.of(info)
        val expectedFingerprint = DataContentFingerprint.localOrNull(ref)
        val request = FormatResolutionRequest(context, ref, input.entry.encoding?.asString())
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
        return ResolvedInput(DataUnit(emptyMap(), listOf(part)), resolution)
    }


    private data class PreparedInput(
        val entry: FileSelectionEntry,
        val info: DataLocationInfo,
        val formatOverride: ConfiguredRecordFormatPreflight?
    )


    private data class ResolvedInput(
        val unit: DataUnit,
        val resolution: FormatResolutionResult
    )
}
