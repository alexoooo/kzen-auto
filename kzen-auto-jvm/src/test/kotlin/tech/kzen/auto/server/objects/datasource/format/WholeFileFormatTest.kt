package tech.kzen.auto.server.objects.datasource.format

import org.junit.After
import org.junit.Test
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.collected
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.prepareCsvFile
import kotlin.test.assertContains
import kotlin.test.assertEquals


class WholeFileFormatTest {
    private val harness = ContentTestHarness()


    @After
    fun tearDown() {
        harness.close()
    }


    @Test
    fun wholeFileSourceEmitsEachFileOnceInsteadOfItsRows() {
        prepareCsvFile("whole", 25)

        val files = collected(harness.run("test/job/content/file-whole-test.yaml"))

        assertEquals(1, files.size)
        assertContains(files.single().toString(), "big.csv")
    }
}
