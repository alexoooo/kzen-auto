package tech.kzen.auto.server.data

import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.server.data.content.SequentialByteContent


/**
 * The opener chain entered BELOW the content provider (content streaming spike CS2,
 * docs/plans/2026-09-16_borrowed-elements.md): a caller that already holds the part's bytes — an entry
 * borrowed from an archive cursor, which no [tech.kzen.auto.common.data.model.DataRef] can re-acquire — hands
 * them in with the part's declared read spec and gets the same reader cursor
 * [tech.kzen.auto.common.data.api.DataOpener.open]
 * would build over a provider-acquired handle: content coding, read policy, fingerprint stamping and cursor
 * ownership are identical. The provider lookup, its descriptor capability check and the fingerprint comparison
 * are what this skips — the part's [DataPart.expectedFingerprint] is taken as observed, since the caller's
 * bytes ARE the observation.
 */
interface ContentDataOpener {
    /** Opens [part] over [bytes]; the cursor owns [bytes] from here, closing them with itself, also on failure. */
    suspend fun openContent(part: DataPart, bytes: SequentialByteContent): DataCursor
}
