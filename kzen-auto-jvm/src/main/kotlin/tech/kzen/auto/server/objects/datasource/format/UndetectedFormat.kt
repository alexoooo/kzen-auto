package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.format.detection.FormatDetectionFailureCategory
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.server.data.read.detection.FormatDetectionException
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue


/**
 * The resolution of a selected file that no installed format claims (borrowed elements BE5, one file selector):
 * the file stays in the selection as opaque bytes, because a whole-file consumer (`Extract`, which an adjacent
 * `File` source feeds unread) never needs a reader; only a reader asked to open it is refused, in the
 * detector's own words ([refusalOrNull]). Detection failures other than [FormatDetectionFailureCategory.Resolution]
 * (a timeout, an unreadable file) still fail the selection.
 */
object UndetectedFormat {
    //-----------------------------------------------------------------------------------------------------------------
    val identity = ReaderCapabilityIdentity("tech.kzen.auto", "undetected", "1")

    private const val displayLabel = "Whole file (not readable)"
    private const val reasonKey = "reason"


    //-----------------------------------------------------------------------------------------------------------------
    fun carries(failure: FormatDetectionException): Boolean =
        failure.category == FormatDetectionFailureCategory.Resolution


    fun resolve(request: FormatResolutionRequest, failure: FormatDetectionException): FormatResolutionResult {
        val reason = failure.message ?: "No installed format claims the input"
        return FormatResolutionResult(
            unread(reason),
            FormatResolutionDetail(
                request.ref,
                null,
                displayLabel,
                FormatSelectionKind.Automatic,
                FormatResolutionBasis.Fallback,
                reason,
                warning = "No installed format reads this file; it can be passed on whole but not read"))
    }


    /** The spec of a file that is carried but never opened, for the stated [reason]. */
    fun unread(reason: String): ResolvedReadSpec =
        ResolvedReadSpec(
            identity,
            listOf(ContentCodingSpec.identity),
            MapExecutionValue(linkedMapOf(reasonKey to TextExecutionValue(reason))))


    /** The detector's refusal when [spec] is an undetected file, null for any real reader. */
    fun refusalOrNull(spec: ResolvedReadSpec): String? {
        if (spec.reader != identity) {
            return null
        }
        val reason = (spec.config as? MapExecutionValue)?.get(reasonKey) as? TextExecutionValue
        return reason?.value ?: displayLabel
    }
}
