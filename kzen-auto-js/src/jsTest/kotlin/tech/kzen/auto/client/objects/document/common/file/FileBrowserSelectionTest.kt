package tech.kzen.auto.client.objects.document.common.file

import tech.kzen.auto.common.util.data.DataLocation
import kotlin.test.Test
import kotlin.test.assertEquals


class FileBrowserSelectionTest {
    private val paths = listOf("a", "b", "c", "d").map(DataLocation::of)


    @Test
    fun shiftRangeExtendsACheckedAnchor() {
        val checkedAnchor = FileBrowserSelection.toggle(paths, emptySet(), 1, null, false)

        assertEquals(
            setOf(paths[1], paths[2], paths[3]),
            FileBrowserSelection.toggle(paths, checkedAnchor, 3, 1, true))
    }


    @Test
    fun shiftRangeClearsAnUncheckedAnchor() {
        val uncheckedAnchor = FileBrowserSelection.toggle(paths, paths.toSet(), 1, null, false)

        assertEquals(
            setOf(paths[0]),
            FileBrowserSelection.toggle(paths, uncheckedAnchor, 3, 1, true))
    }
}
