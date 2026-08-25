package tech.kzen.auto.common.util.data

import kotlinx.serialization.Serializable


/**
 * One directory's browse result: what it holds, and the absolute directory that was actually read.
 *
 * The directory is echoed rather than assumed by the caller because the request may name a relative one — the
 * notation default is `./`, which tells a reader nothing about where they are and cannot be navigated up out of.
 * Only the server knows what a relative path resolves against, so the response carries the resolved form. This is
 * what Report's `browserInfo` action has always done through
 * [tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo]; that one rides the detached-action
 * value-tree plane, this one the kotlinx wire plane, hence two types rather than one.
 *
 * SER3 — SINGLE-PLANE: the generated kotlinx codec below is the only encoding (GET /file-listing).
 */
@Serializable
data class DataListing(
    val directory: DataLocation,
    val files: List<DataLocationInfo>
)
