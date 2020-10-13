package tech.kzen.auto.server.objects.process.pivot.row.value

import kotlin.test.Test
import kotlin.test.assertEquals


class MapRowValueIndexTest {
    @Test
    fun singleElementLookup() {
        val rowValueIndex = MapRowValueIndex()

        val ordinal = rowValueIndex.getOrAddIndex("foo")
        assertEquals(0, ordinal)

        val value = rowValueIndex.getValue(0)
        assertEquals("foo", value)
    }
}