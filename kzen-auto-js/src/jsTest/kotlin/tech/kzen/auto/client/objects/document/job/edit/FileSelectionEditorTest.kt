package tech.kzen.auto.client.objects.document.job.edit

import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.util.data.DataLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue


class FileSelectionEditorTest {
    @Test
    fun emptySelectionPinsBrowserOpen() {
        assertTrue(FileSelectionEditor.browserOpen(selectionEmpty = true, toggledOpen = false))
        assertTrue(FileSelectionEditor.browserOpen(selectionEmpty = true, toggledOpen = true))
    }


    @Test
    fun populatedSelectionFollowsTheToggle() {
        assertFalse(FileSelectionEditor.browserOpen(selectionEmpty = false, toggledOpen = false))
        assertTrue(FileSelectionEditor.browserOpen(selectionEmpty = false, toggledOpen = true))
    }


    @Test
    fun collapsedBrowserDoesNotListUntilItOpens() {
        val triggers = listOf(false, true).map { browserOpen ->
            FileSelectionEditor.shouldLoadListing(browserOpen = browserOpen, directory = "./")
        }

        assertEquals(listOf(false, true), triggers)
        assertEquals(1, triggers.count { it })
    }


    @Test
    fun blankDirectoryNeverLists() {
        assertFalse(FileSelectionEditor.shouldLoadListing(browserOpen = true, directory = ""))
    }


    @Test
    fun failedBrowserCommitReportsErrorWithoutLoading() = async {
        var error: String? = null
        var loaded = false

        FileSelectionEditor.commitBrowserValue(
            apply = { "remote rejected edit" },
            onError = { error = it },
            onCommitted = { loaded = true })

        assertEquals("remote rejected edit", error)
        assertFalse(loaded)
    }


    @Test
    fun successfulBrowserCommitClearsErrorThenLoads() = async {
        var error: String? = "previous error"
        var loaded = false

        FileSelectionEditor.commitBrowserValue(
            apply = { null },
            onError = { error = it },
            onCommitted = { loaded = true })

        assertNull(error)
        assertTrue(loaded)
    }


    @Test
    fun checkedEntriesMoveAsABlock() {
        val moved = FileSelectionEditor.moveChecked(entries("a", "b", "c", "d"), locations("c", "d"), -1)

        assertEquals(order("a", "c", "d", "b"), locationsOf(moved))
    }


    @Test
    fun anEntryAgainstTheEdgeHoldsBackTheRestOfItsBlock() {
        val moved = FileSelectionEditor.moveChecked(entries("a", "b", "c"), locations("a", "b"), -1)

        assertEquals(order("a", "b", "c"), locationsOf(moved))
    }


    @Test
    fun aGapInTheCheckedRunStillMovesBothHalves() {
        val moved = FileSelectionEditor.moveChecked(entries("a", "b", "c", "d"), locations("a", "c"), 1)

        assertEquals(order("b", "a", "d", "c"), locationsOf(moved))
    }


    @Test
    fun movingDownStopsAtTheLastEntry() {
        val moved = FileSelectionEditor.moveChecked(entries("a", "b"), locations("b"), 1)

        assertEquals(order("a", "b"), locationsOf(moved))
    }


    @Test
    fun nothingToMoveLeavesTheListUntouched() {
        val entries = entries("a", "b")

        assertSame(entries, FileSelectionEditor.moveChecked(entries, emptySet(), -1))
        assertSame(entries, FileSelectionEditor.moveChecked(entries, locations("a", "b"), 0))
    }


    private fun entries(vararg names: String): List<FileSelectionEntry> =
        order(*names).map { FileSelectionEntry(it, null, null) }


    private fun locations(vararg names: String): Set<DataLocation> =
        order(*names).toSet()


    private fun order(vararg names: String): List<DataLocation> =
        names.map { DataLocation.of("./$it.csv") }


    private fun locationsOf(entries: List<FileSelectionEntry>): List<DataLocation> =
        entries.map { it.location }
}
