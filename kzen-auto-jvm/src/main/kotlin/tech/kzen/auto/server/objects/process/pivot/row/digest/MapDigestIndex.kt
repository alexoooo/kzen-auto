package tech.kzen.auto.server.objects.process.pivot.row.digest

import tech.kzen.lib.common.util.Digest


class MapDigestIndex: DigestIndex {
    private val index = mutableMapOf<Digest, Int>()


    override fun getOrAddIndex(digest: Digest): Long {
        val existing = index[digest]

        if (existing != null) {
            return existing.toLong()
        }

        val nextIndex = index.size
        index[digest] = nextIndex

        return nextIndex.toLong()
    }
}