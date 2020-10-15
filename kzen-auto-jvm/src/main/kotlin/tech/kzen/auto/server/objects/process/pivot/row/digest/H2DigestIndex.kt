package tech.kzen.auto.server.objects.process.pivot.row.digest

import net.openhft.hashing.LongHashFunction
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import tech.kzen.lib.common.util.Digest
import java.nio.file.Files
import java.nio.file.Path


class H2DigestIndex(
    dir: Path
): DigestIndex {
    //-----------------------------------------------------------------------------------------------------------------
    private var nextOrdinal = 0L

//    private val store: MVStore
    private val storeA: MVStore
    private val storeB: MVStore

//    private val map: MVMap<ByteArray, Long>
    private val mapA: MVMap<ByteArray, Long>
    private val mapB: MVMap<ByteArray, Long>

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
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun size(): Long {
        return nextOrdinal
    }


    override fun getOrAdd(digest: Digest): DigestOrdinal {
        val bytes = digest.toByteArray()

        val map =
            if (binaryPartition(bytes)) {
                mapA
            }
            else {
                mapB
            }

        val existing = map.putIfAbsent(bytes, nextOrdinal)

        if (existing != null) {
            return DigestOrdinal.ofExisting(existing)
        }

        val ordinal = nextOrdinal
        nextOrdinal++
        return DigestOrdinal.ofAdded(ordinal)
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