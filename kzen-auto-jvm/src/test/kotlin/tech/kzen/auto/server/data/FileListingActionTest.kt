package tech.kzen.auto.server.data

import java.nio.file.Files
import kotlin.io.path.createDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import tech.kzen.auto.common.util.data.DataLocation
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
