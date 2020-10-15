package tech.kzen.auto.server.objects.process.pivot.row.digest

import tech.kzen.lib.common.util.Digest


interface DigestIndex: AutoCloseable {
    fun size(): Long

    /**
     * NB: Digest.zero is not allowed
     */
    fun getOrAdd(digest: Digest): DigestOrdinal
}