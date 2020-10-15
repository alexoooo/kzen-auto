package tech.kzen.auto.server.objects.process.pivot.row.digest

import org.mapdb.DBMaker
import org.mapdb.HTreeMap
import org.mapdb.Serializer
import tech.kzen.lib.common.util.Digest
import java.nio.file.Files
import java.nio.file.Path


class MapDbDigestIndex(
    dir: Path
): DigestIndex {
    //-----------------------------------------------------------------------------------------------------------------
    private val map: HTreeMap<ByteArray, Long>

    init {
        Files.createDirectories(dir)

        map = DBMaker
            .fileDB(dir.resolve("mapdb").toFile())
            .executorEnable()
            .fileLockDisable()
            .make()
            .hashMap("hash", Serializer.BYTE_ARRAY, Serializer.LONG)
            .create()
    }


    private var nextOrdinal = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override fun size(): Long {
        return nextOrdinal
    }


    override fun getOrAdd(digest: Digest): DigestOrdinal {
        val asByteArray = digest.toByteArray()

        val existing = map[asByteArray]

        if (existing != null) {
            return DigestOrdinal.ofExisting(existing)
        }

        val ordinal = nextOrdinal
        map[asByteArray] = ordinal
        nextOrdinal++

        return DigestOrdinal.ofAdded(ordinal)
    }


    override fun close() {
        map.close()
    }
}