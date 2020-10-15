package tech.kzen.auto.server.objects.process.pivot.row.digest

import com.google.common.base.Stopwatch
import com.google.common.hash.Hashing
import org.junit.Test
import tech.kzen.lib.common.util.Digest
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse


class FileDigestIndexTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun emptyHasSizeZero() {
        use { index ->
            assertEquals(0, index.size())
        }
    }


    @Test
    fun singleDigest() {
        use { index ->
            assertEquals(0, index.size())

            val initialIndex = index.getOrAdd(Digest.missing)
            assert(initialIndex.wasAdded())
            assertEquals(0, initialIndex.ordinal())
            assertEquals(1, index.size())

            val retrievedIndex = index.getOrAdd(Digest.missing)
            assertFalse(retrievedIndex.wasAdded())
            assertEquals(0, retrievedIndex.ordinal())
        }
    }


    @Test
    fun oneMillionDigest() {
//        for (count in 1 .. 1_000_000) {
//            println("===============================================")
//            println("count: $count")

            use { index ->
    //            val count = 1
    //            val count = 10
    //            val count = 100
    //            val count = 200 // worked
//                val count = 227 // failed @5
    //            val count = 250 // failed @2
    //            val count = 500
//                val count = 1_000
                val count = 100_000
//                val count = 1_000_000
                val builder = Digest.Builder()

                val addTimer = Stopwatch.createStarted()
                for (i in 0 until count) {
                    builder.addInt(i)
//                    val digestRaw = builder.digest()
//                    val digest = mix(digestRaw)
                    val digest = builder.digest()
                    builder.clear()

    //                println("$i - $digest")
                    val digestOrdinal = index.getOrAdd(digest)

                    assert(digestOrdinal.wasAdded())
                    assertEquals(i.toLong(), digestOrdinal.ordinal())
                    assertEquals(i.toLong() + 1, index.size())

                    if ((i + 1) % 10_000 == 0) {
                        println("Added ${i + 1} took $addTimer - " +
                                "${index.bucketCount()} | " +
                                "${i.toDouble() / (index.bucketCount() * 256)} | " +
                                " ${i.toDouble() / addTimer.elapsed(TimeUnit.SECONDS)}")
                    }
                }
                println("Added $count took $addTimer")

                val retrievalTimer = Stopwatch.createStarted()
                for (i in 0 until count) {
                    builder.addInt(i)
//                    val digestRaw = builder.digest()
//                    val digest = mix(digestRaw)
                    val digest = builder.digest()
                    builder.clear()

                    val digestOrdinal = index.getOrAdd(digest)

                    assertFalse(digestOrdinal.wasAdded(), "false added: $i")
                    assertEquals(i.toLong(), digestOrdinal.ordinal())

                    if ((i + 1) % 10_000 == 0) {
                        println("Retrieved ${i + 1} took $retrievalTimer")
                    }
                }
                println("Retrieved $count took $retrievalTimer")
            }
//        }
    }


    private fun mix(digest: Digest): Digest {
        val hashed = Hashing.murmur3_128().hashBytes(digest.toByteArray()).asBytes()
        return Digest.fromBytes(hashed)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun use(consumer: (FileDigestIndex) -> Unit) {
        val dir = createTempDir("FileDigestIndex","").toPath()

        try {
            FileDigestIndex(dir).use {
                consumer.invoke(it)
            }
        }
        finally {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete)
        }
    }
}