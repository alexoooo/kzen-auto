package tech.kzen.auto.server.objects.process.pivot.row.digest

import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap
import net.openhft.hashing.LongHashFunction
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
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var nextOrdinal = 0L

//    private val store: MVStore
    private val storeA: MVStore
    private val storeB: MVStore

//    private val map: MVMap<ByteArray, Long>
    private val mapA: MVMap<ByteArray, Long>
    private val mapB: MVMap<ByteArray, Long>

    private val cache = Long2LongLinkedOpenHashMap()

    init {
        Files.createDirectories(dir)

//        store = MVStore.Builder()
//            .fileName(dir.resolve("h2").toString())
//            .open()
//        map = store.openMap("digest")

        storeA = MVStore.Builder()
            .fileName(dir.resolve("h2-a").toString())
            .open()
        storeB = MVStore.Builder()
            .fileName(dir.resolve("h2-b").toString())
            .open()
        mapA = storeA.openMap("digest-a")
        mapB = storeB.openMap("digest-b")

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

//        val a = (digestHigh shr 32).toInt()
//        val b = digestHigh.toInt()
//        val c = (digestLow shr 32).toInt()
//        val d = digestLow.toInt()
//        val digest = Digest(a, b, c, d)
//        val bytes = digest.toByteArray()

        val map =
            if (binaryPartition(bytes)) {
                mapA
            }
            else {
                mapB
            }

        val existing = map.putIfAbsent(bytes, nextOrdinal)

        val digestOrdinal =
            if (existing != null) {
                DigestOrdinal.ofExisting(existing)
            }
            else {
                val ordinal = nextOrdinal
                nextOrdinal++
                DigestOrdinal.ofAdded(ordinal)
            }

        cache[cacheKey] = digestOrdinal.ordinal()
        if (cache.size == cacheSize) {
            cache.removeFirstLong()
        }

        return digestOrdinal
    }


    private fun binaryPartition(bytes: ByteArray): Boolean {
        return LongHashFunction.xx().hashBytes(bytes) < 0
    }


    override fun close() {
//        store.close()
        storeA.close()
        storeB.close()
    }
}