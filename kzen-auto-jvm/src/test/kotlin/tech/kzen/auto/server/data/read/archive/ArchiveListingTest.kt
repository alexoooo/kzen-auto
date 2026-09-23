package tech.kzen.auto.server.data.read.archive

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.junit.After
import org.junit.Test
import tech.kzen.auto.plugin.api.data.ReaderByteInput
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.collected
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.prepare
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataConstraint
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.value.DataValueAlgebra
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.recordOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals


class ArchiveListingTest {
    private val harness = ContentTestHarness()


    @After
    fun tearDown() {
        harness.close()
    }


    @Test
    fun fileSourceOverATarGzListsItsMembersWithoutReadingThem() {
        prepare("listing", listOf(
            "docs/" to null,
            "docs/foo.txt" to "hello".toByteArray(),
            "bar.csv" to "a,b\n1,2\n".toByteArray()))

        val rows = collected(harness.run("test/job/content/file-listing-test.yaml")).map { it as Map<*, *> }

        assertEquals(listOf("docs/", "docs/foo.txt", "bar.csv"), rows.map { it["name"] })
        assertEquals(listOf("directory", "file", "file"), rows.map { it["kind"] })
        assertEquals(listOf("0", "5", "8"), rows.map { it["size"].toString() })
    }


    @Test
    fun everyMemberKindConformsToTheDeclaredSymbolSet() {
        val kindPath = DataTypePath(listOf(DataPathSegment.Field(FieldId(ArchiveListingCursor.kindField))))
        assertEquals(
            listOf(DataConstraint.SymbolSet(listOf("file", "directory", "link", "other"))),
            ArchiveListingCursor.contract.constraintsByPath[kindPath])

        val tar = ByteArrayOutputStream().also { bytes ->
            TarArchiveOutputStream(bytes).use { out ->
                out.putArchiveEntry(TarArchiveEntry("docs/"))
                out.closeArchiveEntry()
                val body = "hello".toByteArray()
                out.putArchiveEntry(TarArchiveEntry("docs/foo.txt").also { it.size = body.size.toLong() })
                out.write(body)
                out.closeArchiveEntry()
                out.putArchiveEntry(TarArchiveEntry("soft", TarConstants.LF_SYMLINK).also { it.linkName = "docs/foo.txt" })
                out.closeArchiveEntry()
                out.putArchiveEntry(TarArchiveEntry("hard", TarConstants.LF_LINK).also { it.linkName = "docs/foo.txt" })
                out.closeArchiveEntry()
                out.putArchiveEntry(TarArchiveEntry("pipe", TarConstants.LF_FIFO))
                out.closeArchiveEntry()
            }
        }.toByteArray()

        val kinds = mutableListOf<String>()
        ArchiveListingCursor(input(tar), ArchiveListingReaderCapability.shape).use { cursor ->
            while (cursor.hasNext()) {
                val row = cursor.next()
                assertEquals(emptyList(), DataValueAlgebra.validate(ArchiveListingCursor.contract, row))
                kinds += row.access.readText(row.access.field(row.root, FieldId(ArchiveListingCursor.kindField)))
            }
        }
        assertEquals(listOf("directory", "file", "link", "link", "other"), kinds)

        val stray = LiteralDataValues.lift(
            recordOf("name" to "x", "size" to 0L, "modified" to null, "kind" to "socket"),
            ArchiveListingCursor.contract)
        assertEquals(
            listOf(DataProblem.constraintViolation),
            DataValueAlgebra.validate(ArchiveListingCursor.contract, stray).map { it.code })
    }


    private fun input(bytes: ByteArray): ReaderByteInput {
        val stream = ByteArrayInputStream(bytes)
        return object: ReaderByteInput {
            override var expandedBytesRead = 0L
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                stream.read(buffer, offset, length).also { if (it > 0) expandedBytesRead += it }
        }
    }
}
