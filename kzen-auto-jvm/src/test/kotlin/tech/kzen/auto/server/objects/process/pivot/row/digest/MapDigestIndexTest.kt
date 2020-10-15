package tech.kzen.auto.server.objects.process.pivot.row.digest

import tech.kzen.lib.common.util.Digest
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
}