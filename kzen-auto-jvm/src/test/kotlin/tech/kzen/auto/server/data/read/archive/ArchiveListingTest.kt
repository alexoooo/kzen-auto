package tech.kzen.auto.server.data.read.archive

import org.junit.After
import org.junit.Test
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.collected
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.prepare
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
}
