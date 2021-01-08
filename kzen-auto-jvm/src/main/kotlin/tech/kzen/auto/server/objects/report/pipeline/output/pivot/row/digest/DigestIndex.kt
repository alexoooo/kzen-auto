package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.digest

import tech.kzen.lib.common.util.Digest


// TODO: try B-Tree implementation, e.g. https://github.com/myui/btree4j/
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


    fun add(digestHigh: Long, digestLow: Long): DigestOrdinal {
        val ordinal = getOrAdd(digestHigh, digestLow)
        check(ordinal.wasAdded())
        return ordinal
    }
}