package tech.kzen.auto.common.data.format

import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.format.detection.FormatHintMetadata
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.lib.common.util.digest.Digestible


/** Resolved, immutable read configuration selected by a data source. */
interface ConfiguredRecordFormat: Digestible {
    val title: String
    val extensions: List<String>
    val catalogVisible: Boolean

    val selectionKind: FormatSelectionKind
        get() = FormatSelectionKind.Explicit

    val automaticDetectionCandidate: Boolean
        get() = true

    val automaticTextFallback: Boolean
        get() = false

    val automaticDetectionTemplate: Boolean
        get() = false

    val hintMetadata: List<FormatHintMetadata>
        get() = extensions.map { extension ->
            FormatHintMetadata.structured(extension, listOf(extension))
        }

    val compatibleStructuredFamilies: List<String>
        get() = hintMetadata.mapNotNull(FormatHintMetadata::structuredFamily).distinct().sorted()

    val authoringCapabilityIdentity: String?
        get() = null

    val overrideEditorReference: String?
        get() = null

    val columnsLocked: Boolean
        get() = false


    @Suppress("DEPRECATION")
    suspend fun resolve(request: FormatResolutionRequest): FormatResolutionResult {
        require(request.explicitEncoding == null) {
            "$title does not support an explicit encoding override"
        }
        return FormatResolutionResult(
            resolvedRead(request.ref),
            FormatResolutionDetail(
                request.ref,
                null,
                title,
                selectionKind,
                FormatResolutionBasis.Override,
                "$title was selected explicitly",
                columnsLocked = columnsLocked))
    }

    @Deprecated("Use resolve(request) so contextual overrides and provenance are preserved")
    fun resolvedRead(ref: DataRef): ResolvedReadSpec

    fun declaredShape(): DataShape?
}
