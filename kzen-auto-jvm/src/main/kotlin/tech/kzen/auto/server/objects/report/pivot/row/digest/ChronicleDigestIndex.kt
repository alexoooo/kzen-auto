package tech.kzen.auto.server.objects.report.pivot.row.digest

//import net.openhft.chronicle.map.ChronicleMapBuilder
//import tech.kzen.lib.common.util.Digest
//import java.nio.file.Path
//
//
//class ChronicleDigestIndex(
//    private val dir: Path
//): DigestIndex {
//    //-----------------------------------------------------------------------------------------------------------------
//    private var nextOrdinal = 0L
//
//
//    init {
//        ChronicleMapBuilder
//            .of(ByteArray::class.java, ByteArray::class.java)
//            .name("p0")
//            .averageKeySize((4 * Integer.BYTES).toDouble())
//            .averageValueSize(Long.SIZE_BYTES.toDouble())
//            .entries(1024 * 1024)
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun size(): Long {
//        return nextOrdinal
//    }
//
//    override fun getOrAdd(digest: Digest): DigestOrdinal {
//        TODO("Not yet implemented")
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun close() {
//    }
//}