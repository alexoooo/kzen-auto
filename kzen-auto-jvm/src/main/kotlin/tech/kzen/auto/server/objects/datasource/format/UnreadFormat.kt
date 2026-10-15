package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue


/**
 * The resolution of a selected file whose name selects no format under Automatic (`FileFormatNames`): the file
 * stays in the selection as opaque bytes, because a whole-file consumer (`Extract`, which an adjacent `File` source
 * feeds unread) never needs a reader; only a reader asked to open it is refused, with the reason ([refusalOrNull]).
 */
object UnreadFormat {
    //-----------------------------------------------------------------------------------------------------------------
    val identity = ReaderCapabilityIdentity("tech.kzen.auto", "unread", "1")

    private const val displayLabel = "Whole file (not readable)"
    private const val reasonKey = "reason"


    //-----------------------------------------------------------------------------------------------------------------
    fun resolve(request: FormatResolutionRequest, reason: String): FormatResolutionResult {
        return FormatResolutionResult(
            unread(reason),
            FormatResolutionDetail(
                request.ref,
                null,
                displayLabel,
                FormatSelectionKind.Automatic,
                FormatResolutionBasis.Fallback,
                reason,
                warning = "No format is named by this file's extension; it can be passed on whole but not read"))
    }


    /** The spec of a file that is carried but never opened, for the stated [reason]. */
    fun unread(reason: String): ResolvedReadSpec =
        ResolvedReadSpec(
            identity,
            listOf(ContentCodingSpec.identity),
            MapExecutionValue(linkedMapOf(reasonKey to TextExecutionValue(reason))))


    /** Why [spec] cannot be read when it is a file carried unread, null for any real reader. */
    fun refusalOrNull(spec: ResolvedReadSpec): String? {
        if (spec.reader != identity) {
            return null
        }
        val reason = (spec.config as? MapExecutionValue)?.get(reasonKey) as? TextExecutionValue
        return reason?.value ?: displayLabel
    }
}
