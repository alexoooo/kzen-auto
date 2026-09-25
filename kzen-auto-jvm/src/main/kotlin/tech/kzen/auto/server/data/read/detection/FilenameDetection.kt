package tech.kzen.auto.server.data.read.detection

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.detection.FormatHintClass
import tech.kzen.auto.common.data.format.detection.FormatHintMetadata
import tech.kzen.auto.common.data.format.detection.NormalizedFormatHints
import tech.kzen.auto.common.data.schema.DataShape


/**
 * What automatic detection concludes from a file's name alone, shared by [AutomaticFormatResolver] and the static
 * contract walk so the two cannot disagree about which formats a name admits.
 */
object FilenameDetection {
    fun hints(fileName: String): NormalizedFormatHints {
        val extension = fileName.substringAfterLast('.', "").takeIf(String::isNotEmpty)
        return NormalizedFormatHints.of(filenameExtension = extension)
    }


    /** A `.gz` suffix names a content coding, so the hint is the extension beneath it (`data.tar.gz` → `tar`). */
    fun effectiveHints(hints: NormalizedFormatHints, refId: String): NormalizedFormatHints {
        val supplied = hints.filenameExtension
        val filename = refId.substringBefore('?').substringBefore('#')
        val gzipSuffix = filename.endsWith(".gz", ignoreCase = true)
        val extension = if (supplied == "gz" || (supplied == null && gzipSuffix)) {
            filename.dropLast(3)
                .substringAfterLast('.', "").takeIf(String::isNotEmpty)
        }
        else supplied
        return NormalizedFormatHints.of(extension, hints.mediaType, hints.providerHints)
    }


    fun classify(
        hints: NormalizedFormatHints,
        metadata: List<FormatHintMetadata>
    ): FormatHintMetadata? {
        val matches = metadata.filter { hint ->
            hints.filenameExtension in hint.extensions || hints.mediaType in hint.mediaTypes
        }.distinct()
        if (matches.isEmpty()) return null
        val meanings = matches.map { it.hintClass to it.structuredFamily }.distinct()
        require(meanings.size == 1) {
            "Format hint '${hints.filenameExtension ?: hints.mediaType}' has conflicting classifications"
        }
        return matches.first()
    }


    /** Whether a candidate may resolve input whose name carries the structured-family [hint]. */
    fun structuredEligible(
        hints: NormalizedFormatHints,
        hint: FormatHintMetadata,
        exactExtensions: List<String>,
        structuredFamilies: List<String>
    ): Boolean =
        hints.filenameExtension in exactExtensions || hint.structuredFamily in structuredFamilies


    fun exactExtensions(format: ConfiguredRecordFormat): List<String> =
        format.extensions.map { it.trim().removePrefix(".").lowercase() }.distinct().sorted()


    fun structuredFamilies(format: ConfiguredRecordFormat): List<String> =
        format.compatibleStructuredFamilies.map { it.trim().lowercase() }.distinct().sorted()


    /**
     * The shape automatic detection over [formats] yields for [fileName] whenever it succeeds, or null when the
     * content decides it. A structured-family name never falls back to text: detection either validates one of
     * the eligible formats or fails, so when every eligible format declares the same shape, the name fixes it.
     */
    fun declaredShape(fileName: String, formats: List<ConfiguredRecordFormat>): DataShape? {
        val hints = effectiveHints(hints(fileName), fileName)
        val hint = classify(hints, formats.flatMap { it.hintMetadata }.distinct())
        if (hint?.hintClass != FormatHintClass.StructuredFamily) {
            return null
        }
        return formats
            .filter {
                it.automaticDetectionCandidate &&
                    structuredEligible(hints, hint, exactExtensions(it), structuredFamilies(it))
            }
            .map { it.declaredShape() }
            .distinct()
            .singleOrNull()
    }


    /**
     * The one format [fileName] alone selects among [formats], for content that cannot be sampled ahead of
     * reading (a member of an archive, read once from its stream): the automatic candidate whose extensions
     * name the file's, else the single candidate of its structured family; null when the name admits none or
     * several.
     */
    fun formatFor(fileName: String, formats: List<ConfiguredRecordFormat>): ConfiguredRecordFormat? {
        val hints = effectiveHints(hints(fileName), fileName)
        val extension = hints.filenameExtension?.lowercase()
            ?: return null
        val candidates = formats.filter { it.automaticDetectionCandidate }
        val exact = candidates.filter { extension in exactExtensions(it) }
        if (exact.size == 1) {
            return exact.single()
        }
        val hint = classify(hints, formats.flatMap { it.hintMetadata }.distinct())
        if (hint?.hintClass != FormatHintClass.StructuredFamily) {
            return null
        }
        return candidates
            .filter { structuredEligible(hints, hint, exactExtensions(it), structuredFamilies(it)) }
            .singleOrNull()
    }
}
