package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.digest

import com.google.common.base.Stopwatch
import tech.kzen.lib.common.util.Digest
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse


class MapDigestIndexTest {
    @Test
    fun emptyHasSizeZero() {
        assertEquals(0, MapDigestIndex().size())
    }

    @Test
    fun singleDigest() {
        val digestIndex = MapDigestIndex()

        val initialIndex = digestIndex.getOrAdd(Digest.empty)
        assert(initialIndex.wasAdded())
        assertEquals(0, initialIndex.ordinal())
        assertEquals(1, digestIndex.size())

        val retrievedIndex = digestIndex.getOrAdd(Digest.empty)
        assertFalse(retrievedIndex.wasAdded())
        assertEquals(0, retrievedIndex.ordinal())
    }

    @Test
    fun oneThousandDigest() {
        val index = MapDigestIndex()

        val count = 1_000
//        val count = 100_000
//        val count = 1_000_000

        val builder = Digest.Builder()

        val addTimer = Stopwatch.createStarted()
        for (i in 0 until count) {
            builder.addInt(i)
            val digest = builder.digest()
            builder.clear()

            //                println("$i - $digest")
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