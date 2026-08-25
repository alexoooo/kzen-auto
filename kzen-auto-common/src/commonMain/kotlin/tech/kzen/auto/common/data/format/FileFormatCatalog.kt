package tech.kzen.auto.common.data.format

import tech.kzen.auto.common.objects.document.plugin.model.ReportDefinerDetail


/**
 * What a file data source may be told to read with: the registered formats, and the text encodings the server
 * can decode. These are the option lists behind the Format / Encoding selects — a Worker's defaults under
 * Advanced, and the per-file overrides under a selection's Details.
 *
 * Answered by `DataSourceActions` from the same definition registry the reader itself resolves against, so what
 * the UI offers is exactly what the server can honour, and a plugin that registers a format appears here with no
 * client change. Neither list is guessable by typing, which is why they are served rather than free text.
 *
 * `asCollection` / `ofCollection` because this travels as an `ExecutionValue`, like every other detached-action
 * reply; [ReportDefinerDetail] is reused verbatim so a format means the same thing here as it does in a Report.
 */
data class FileFormatCatalog(
    val formats: List<ReportDefinerDetail>,
    val encodings: List<String>
) {
    companion object {
        private const val formatsKey = "formats"
        private const val encodingsKey = "encodings"

        val empty = FileFormatCatalog(listOf(), listOf())


        @Suppress("UNCHECKED_CAST")
        fun ofCollection(collection: Map<String, Any?>): FileFormatCatalog {
            return FileFormatCatalog(
                (collection[formatsKey] as List<Map<String, Any?>>).map(ReportDefinerDetail::ofCollection),
                collection[encodingsKey] as List<String>)
        }
    }


    fun asCollection(): Map<String, Any?> {
        return mapOf(
            formatsKey to formats.map { it.asCollection() },
            encodingsKey to encodings)
    }
}
