package tech.kzen.auto.server.objects.report.exec.output.pivot.row.digest

import io.lacuna.bifurcan.DurableEncodings
import io.lacuna.bifurcan.IDurableEncoding
import io.lacuna.bifurcan.IMap
import io.lacuna.bifurcan.utils.Iterators
import tech.kzen.lib.common.util.digest.Digest
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong


class BifurcanDigestIndex(
    private val dir: Path
):
    DigestIndex
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val missingSentinel = java.lang.Long.valueOf(-1)

//        private const val commitFrequency = 10_000L
//        private const val saveFrequency = 100_000L
        private const val saveFrequency = 250_000L

        // TODO
//        private const val compactFrequency = 2


        private val digestPrimitive: IDurableEncoding.Primitive =
            DurableEncodings.primitive(
                "digest",
                1 shl 9,
                DurableEncodings.Codec.from(
                    { list, out ->
                        for (o in list) {
                            val n = o as Digest
                            out.writeInt(n.a)
                            out.writeInt(n.b)
                            out.writeInt(n.c)
                            out.writeInt(n.d)
                        }
                    }
                ) { input, _ ->
                    Iterators.skippable(
                        Iterators.from(input::hasRemaining) {
                            val a = input.readInt()
                            val b = input.readInt()
                            val c = input.readInt()
                            val d = input.readInt()
                            Digest(a, b, c, d)
                        })
                })

        private val int64Primitive: IDurableEncoding.Primitive =
            DurableEncodings.primitive(
                "int64",
                1 shl 10,
                DurableEncodings.Codec.from(
                    { l, out ->
                        var prev: Long = 0
                        for (o in l) {
                            val n = o as Long
                            out.writeVLQ(n - prev)
                            prev = n
                        }
                    }
                ) { input, _ ->
                    val v = AtomicLong(0)
                    Iterators.skippable(
                        Iterators.from(input::hasRemaining) { v.addAndGet(input.readVLQ()) })
                })

        private val mapEncoding = DurableEncodings.map(digestPrimitive, int64Primitive)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var nextOrdinal = 0L
    private var head: IMap<Digest, Long> = io.lacuna.bifurcan.Map()
//    private var saveRemaining = compactFrequency


    //-----------------------------------------------------------------------------------------------------------------
    init {
        Files.createDirectories(dir)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun size(): Long {
        return nextOrdinal
    }


    override fun getOrAdd(digestHigh: Long, digestLow: Long): DigestOrdinal {
        val a = (digestHigh shr 32).toInt()
        val b = digestHigh.toInt()
        val c = (digestLow shr 32).toInt()
        val d = digestLow.toInt()
        val digest = Digest(a, b, c, d)

        val existingOrdinal = head.get(digest, missingSentinel)

        if (existingOrdinal != missingSentinel) {
            return DigestOrdinal.ofExisting(existingOrdinal)
        }

        val ordinal = nextOrdinal
        head = head.put(digest, ordinal)
        nextOrdinal++

        if (nextOrdinal % saveFrequency == 0L) {
//            val saved = head.save(mapEncoding, dir)
            head = head.save(mapEncoding, dir)

//            saveRemaining--
//            if (saveRemaining == 0) {
//                val compacted = saved.compact()
//
////                head = head
//            }
        }

        return DigestOrdinal.ofAdded(ordinal)
    }


    override fun close() {

    }
}