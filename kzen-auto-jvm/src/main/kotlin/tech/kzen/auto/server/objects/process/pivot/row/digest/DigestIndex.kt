package tech.kzen.auto.server.objects.process.pivot.row.digest

import tech.kzen.lib.common.util.Digest


interface DigestIndex: AutoCloseable {
    fun size(): Long

    /**
     * @return non-negative consecutive index of given existing digest,
     *  or if this is a previously unseen digest then it is added and return is: -(next index + 1)
     */
    fun getOrAdd(digest: Digest): DigestOrdinal
}