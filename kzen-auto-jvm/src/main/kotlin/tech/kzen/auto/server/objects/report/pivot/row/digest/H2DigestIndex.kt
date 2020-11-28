package tech.kzen.auto.server.objects.report.pivot.row.digest

import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import java.nio.file.Files
import java.nio.file.Path


class H2DigestIndex(
    dir: Path,
    private val cacheSize: Int = 64 * 1024
): DigestIndex {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val missingOrdinal = -1L

//        private const val alwaysAdd = true
        private const val alwaysAdd = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var nextOrdinal: Long

    private val storeA: MVStore
    private val storeB: MVStore

    private val mapA: MVMap<ByteArray, Long>
    private val mapB: MVMap<ByteArray, Long>

    private val cache = Long2LongLinkedOpenHashMap()

    init {
        Files.createDirectories(dir)

        storeA = MVStore.Builder()
            .fileName(dir.resolve("h2-a").toString())
            .open()
        storeB = MVStore.Builder()
            .fileName(dir.resolve("h2-b").toString())
            .open()

        val mapBuilder = MVMap.Builder<ByteArray, Long>()
            .singleWriter()

        mapA = storeA.openMap("digest-a", mapBuilder)
        mapB = storeB.openMap("digest-b", mapBuilder)

        nextOrdinal = mapA.sizeAsLong() + mapB.sizeAsLong()

        cache.defaultReturnValue(missingOrdinal)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun size(): Long {
        return nextOrdinal
    }


    override fun getOrAdd(digestHigh: Long, digestLow: Long): DigestOrdinal {
        val cacheKey = digestHigh * 37 xor digestLow
        val cachedOrdinal = cache.getAndMoveToLast(cacheKey)
        if (cachedOrdinal != missingOrdinal) {
            return DigestOrdinal.ofExisting(cachedOrdinal)
        }

        val bytes = byteArrayOf(
            digestHigh.toByte(),
            (digestHigh shr 8).toByte(),
            (digestHigh shr 16).toByte(),
            (digestHigh shr 24).toByte(),
            (digestHigh shr 32).toByte(),
            (digestHigh shr 40).toByte(),
            (digestHigh shr 48).toByte(),
            (digestHigh shr 56).toByte(),
            digestLow.toByte(),
            (digestLow shr 8).toByte(),
            (digestLow shr 16).toByte(),
            (digestLow shr 24).toByte(),
            (digestLow shr 32).toByte(),
            (digestLow shr 40).toByte(),
            (digestLow shr 48).toByte(),
            (digestLow shr 56).toByte())

        val map =
            if (binaryPartition(bytes)) {
                mapA
            }
            else {
                mapB
            }

        val digestOrdinal =
            if (alwaysAdd) {
                map.append(bytes, nextOrdinal)

                val ordinal = nextOrdinal
                nextOrdinal++
                DigestOrdinal.ofAdded(ordinal)
            }
            else {
                val existing = map.putIfAbsent(bytes, nextOrdinal)

                if (existing != null) {
                    DigestOrdinal.ofExisting(existing)
                }
                else {
                    val ordinal = nextOrdinal
                    nextOrdinal++
                    DigestOrdinal.ofAdded(ordinal)
                }
            }

//        val digestOrdinal =
//            if (existing != null) {
//                DigestOrdinal.ofExisting(existing)
//            }
//            else {
//                val ordinal = nextOrdinal
//                nextOrdinal++
//                DigestOrdinal.ofAdded(ordinal)
//            }

        cache[cacheKey] = digestOrdinal.ordinal()
        if (cache.size == cacheSize) {
            cache.removeFirstLong()
        }

        return digestOrdinal
    }


    private fun binaryPartition(bytes: ByteArray): Boolean {
        return bytes.sum() >= 0
    }


    override fun close() {
        storeA.close()
        storeB.close()
    }
}