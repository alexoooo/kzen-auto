package tech.kzen.auto.server.objects.process.pivot.row.digest

import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import tech.kzen.lib.common.util.Digest


class MapDigestIndex: DigestIndex {
    companion object {
        private const val missingSentinel: Int = -1
    }


    private val index: Object2IntMap<Digest> = Object2IntOpenHashMap()


    init {
        index.defaultReturnValue(missingSentinel)
    }


    override fun size(): Long {
        return index.size.toLong()
    }


    override fun getOrAdd(digest: Digest): DigestOrdinal {
        val existing = index.getInt(digest)

        if (existing != missingSentinel) {
            return DigestOrdinal.ofExisting(existing.toLong())
        }

        val nextIndex = index.size
        index[digest] = nextIndex

        return DigestOrdinal.ofAdded(nextIndex.toLong())
    }


    override fun close() {}
}