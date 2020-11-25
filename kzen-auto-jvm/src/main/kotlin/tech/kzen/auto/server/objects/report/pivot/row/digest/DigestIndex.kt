package tech.kzen.auto.server.objects.report.pivot.row.digest

import tech.kzen.lib.common.util.Digest


interface DigestIndex: AutoCloseable {
    fun size(): Long

    fun getOrAdd(digest: Digest): DigestOrdinal {
        val digestHigh = digest.a.toLong() shl 32 or (digest.b.toLong() and 0xffffffffL)
        val digestLow = digest.c.toLong() shl 32 or (digest.d.toLong() and 0xffffffffL)
        return getOrAdd(digestHigh, digestLow)
    }

    /**
     * NB: Digest.zero is not allowed
     */
    fun getOrAdd(digestHigh: Long, digestLow: Long): DigestOrdinal
}