package tech.kzen.auto.server.objects.pipeline.exec.output.pivot.row.digest

import com.google.common.base.Stopwatch
import org.junit.Test
import tech.kzen.lib.common.util.Digest
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse


class H2DigestIndexTest {
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
    fun oneThousandDigest() {
        use { index ->
            val count = 1_000
//            val count = 100_000
//            val count = 1_000_000
//            val count = 10_000_000

            val builder = Digest.Builder()

            val addTimer = Stopwatch.createStarted()
            for (i in 0 until count) {
                builder.addInt(i)
                val digest = builder.digest()
                builder.clear()

                val digestOrdinal = index.getOrAdd(digest)

                assert(digestOrdinal.wasAdded())
                assertEquals(i.toLong(), digestOrdinal.ordinal())
                assertEquals(i.toLong() + 1, index.size())

                if ((i + 1) % 10_000 == 0) {
                    println("Added ${i + 1} took $addTimer - " +
                            " ${i.toDouble() / addTimer.elapsed(TimeUnit.SECONDS)}")
                }
            }
            println("Added $count took $addTimer")

            val retrievalTimer = Stopwatch.createStarted()
            for (i in 0 until count) {
                builder.addInt(i)
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
    }


    //-----------------------------------------------------------------------------------------------------------------
//    @OptIn(ExperimentalPathApi::class)
    private fun use(consumer: (H2DigestIndex) -> Unit) {
        val dir = kotlin.io.path.createTempDirectory("H2DigestIndex")

        try {
            H2DigestIndex(dir).use {
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