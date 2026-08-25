package tech.kzen.auto.server.data

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.FilePath
import tech.kzen.auto.common.util.data.FilePathJvm.of
import tech.kzen.auto.server.service.plugin.HostReportDefinitionRepository
import tech.kzen.auto.server.util.WorkUtils


class FileListingActionTest {
    private val listing = FileListingAction(HostReportDefinitionRepository(emptyList()))


    @Test
    fun browseKeepsDirectoriesVisibleUnderActiveFileFilterAndSortsThemFirst() {
        val directory = Files.createTempDirectory("file-listing-browse")
        try {
            directory.resolve("z-folder").createDirectory()
            directory.resolve("a-folder").createDirectory()
            directory.resolve("match-b.csv").writeText("b")
            directory.resolve("other.csv").writeText("other")
            directory.resolve("match-a.csv").writeText("a")

            val result = listing.browseInfoBlocking(DataLocation.of(directory.toString()), "match")

            assertEquals(
                listOf("a-folder", "z-folder", "match-a.csv", "match-b.csv"),
                result.map { it.name })
            assertEquals(listOf(true, true, false, false), result.map { it.directory })
        }
        finally {
            WorkUtils.recursivelyDeleteDir(directory)
        }
    }


    // What the chooser's path field shows, and what its parent links are built from. The stored value is `./` by
    // default, which names no place a reader would recognize and has no parent to walk up to, so the reply has to
    // say where that resolved to — the client cannot know the server's working directory.
    @Test
    fun browseListingNamesTheAbsoluteDirectoryItRead() {
        val expected = DataLocation.ofFile(FilePath.of(Path.of(".")))

        val relative = runBlocking { listing.browseListing(DataLocation.of("./"), "") }

        assertEquals(expected, relative.directory)
        assertTrue(relative.directory.asString().length > 1, relative.directory.asString())
        assertEquals(listing.browseInfoBlocking(DataLocation.of("./"), ""), relative.files)
    }


    @Test
    fun runtimeScanRemainsFilesOnly() {
        val directory = Files.createTempDirectory("file-listing-scan")
        try {
            directory.resolve("match-folder").createDirectory()
            directory.resolve("match.csv").writeText("match")

            val result = listing.scanInfoBlocking(DataLocation.of(directory.toString()), "match")

            assertEquals(listOf("match.csv"), result.map { it.name })
            assertFalse(result.single().directory)
        }
        finally {
            WorkUtils.recursivelyDeleteDir(directory)
        }
    }
}
