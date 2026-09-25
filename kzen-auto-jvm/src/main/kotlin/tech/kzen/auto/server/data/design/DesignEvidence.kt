package tech.kzen.auto.server.data.design

import tech.kzen.lib.common.util.digest.Digest


/**
 * One thing a design-time read looked at: [subject] names it, [digest] is what it was, and [recheck] computes it
 * again (null when it can no longer be computed). A cached validation is reused only while every piece of its
 * evidence rechecks to the same digest.
 */
class DesignEvidence(
    val subject: String,
    val digest: Digest,
    private val recheck: () -> Digest?
) {
    fun unchanged(): Boolean =
        try {
            recheck() == digest
        }
        catch (_: Exception) {
            false
        }
}
