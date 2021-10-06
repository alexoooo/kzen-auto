package tech.kzen.auto.server.objects.report.exec.output.pivot.row.digest

import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import tech.kzen.auto.server.objects.report.exec.output.pivot.row.digest.bloom.DigestBloomFilter
import java.nio.file.Files
import java.nio.file.Path


class H2DigestIndex(
    dir: Path,
    private val cacheSize: Int = 64 * 1024
): DigestIndex {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val missingOrdinal = -1L
        private const val maxFalsePositive = 0.5

//        private const val alwaysAdd = true
//        private const val alwaysAdd = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var nextOrdinal: Long

    private val storeA: MVStore
    private val storeB: MVStore

    private val mapA: MVMap<ByteArray, Long>
    private val mapB: MVMap<ByteArray, Long>

    private val cache = Long2LongLinkedOpenHashMap()
    private val bloom = DigestBloomFilter(dir)


    //-----------------------------------------------------------------------------------------------------------------
    init {
        Files.createDirectories(dir)

        storeA = MVStore.Builder()
            .fileName(dir.resolve("a.h2").toString())
            .open()
        storeB = MVStore.Builder()
            .fileName(dir.resolve("b.h2").toString())
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


    //-----------------------------------------------------------------------------------------------------------------
    override fun add(digestHigh: Long, digestLow: Long): DigestOrdinal {
        val bytes = digestBytes(digestHigh, digestLow)
        val map = selectMap(bytes)

        val digestOrdinal = append(bytes, map)

        val cacheKey = cacheKey(digestHigh, digestLow)
        addToCache(cacheKey, digestOrdinal.ordinal())
        bloom.add(bytes)

        return digestOrdinal
    }


    override fun getOrAdd(digestHigh: Long, digestLow: Long): DigestOrdinal {
        val cacheKey = cacheKey(digestHigh, digestLow)
        val cachedOrdinal = cache.getAndMoveToLast(cacheKey)
        if (cachedOrdinal != missingOrdinal) {
            return DigestOrdinal.ofExisting(cachedOrdinal)
        }

        val bytes = digestBytes(digestHigh, digestLow)
        val map = selectMap(bytes)

        val mightContain =
            bloom.falsePositiveProbability(nextOrdinal) >= maxFalsePositive ||
            bloom.mightContain(bytes)

        val digestOrdinal =
            if (! mightContain) {
                bloom.add(bytes)
                append(bytes, map)
            }
            else {
                getOrAdd(bytes, map)
            }

        addToCache(cacheKey, digestOrdinal.ordinal())

        return digestOrdinal
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun append(bytes: ByteArray, map: MVMap<ByteArray, Long>): DigestOrdinal {
        map.append(bytes, nextOrdinal)

        val ordinal = nextOrdinal
        nextOrdinal++
        return DigestOrdinal.ofAdded(ordinal)
    }


    private fun getOrAdd(bytes: ByteArray, map: MVMap<ByteArray, Long>): DigestOrdinal {
        val existing = map.putIfAbsent(bytes, nextOrdinal)

        return when {
            existing != null ->
                DigestOrdinal.ofExisting(existing)

            else -> {
                val ordinal = nextOrdinal
                nextOrdinal++
                DigestOrdinal.ofAdded(ordinal)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun cacheKey(digestHigh: Long, digestLow: Long): Long {
        return digestHigh * 37 xor digestLow
    }


    private fun addToCache(cacheKey: Long, ordinal: Long) {
        cache[cacheKey] = ordinal

        if (cache.size == cacheSize) {
            cache.removeFirstLong()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun digestBytes(digestHigh: Long, digestLow: Long): ByteArray {
        return byteArrayOf(
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
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun selectMap(bytes: ByteArray): MVMap<ByteArray, Long> {
        return when {
            binaryPartition(bytes) ->
                mapA

            else ->
                mapB
        }
    }


    private fun binaryPartition(bytes: ByteArray): Boolean {
        return bytes[0] >= 0
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        storeA.close()
        storeB.close()
        bloom.close()
    }
}