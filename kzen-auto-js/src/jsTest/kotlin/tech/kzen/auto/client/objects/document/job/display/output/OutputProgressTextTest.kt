package tech.kzen.auto.client.objects.document.job.display.output

import tech.kzen.auto.client.objects.document.job.JobWorkerProgress
import tech.kzen.auto.common.objects.document.job.JobOutputConventions
import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class OutputProgressTextTest {
    @Test
    fun formatSaysRecordsAndBytes() {
        val progress = progress(mapOf(
            JobOutputConventions.formatRecordsKey to 12_345.0,
            JobOutputConventions.formatBytesKey to 3_250_586L))

        assertEquals("12,345 records · 3.1 MB", OutputProgressText.format(progress))
        assertEquals("1 record", OutputProgressText.format(progress(mapOf(
            JobOutputConventions.formatRecordsKey to 1L,
            JobOutputConventions.formatBytesKey to 0L))))
        assertNull(OutputProgressText.format(null))
    }


    @Test
    fun formatCountsGroupsAndSaysHowManyAreOpenWhileOnlySomeAre() {
        fun grouped(open: Long, done: Long) = OutputProgressText.format(progress(mapOf(
            JobOutputConventions.formatRecordsKey to 10L,
            JobOutputConventions.formatBytesKey to 0L,
            JobOutputConventions.formatGroupsOpenKey to open,
            JobOutputConventions.formatGroupsDoneKey to done)))

        assertEquals("10 records · 4 groups", grouped(4, 0))
        assertEquals("10 records · 4 groups · 1 open", grouped(1, 3))
        assertEquals("10 records · 4 groups", grouped(0, 4))
        assertEquals("10 records · 1 group", grouped(1, 0))
    }


    @Test
    fun writeNamesTheOpenFileThenCountsThePublishedOnes() {
        val writing = progress(mapOf(
            JobOutputConventions.writeFilesKey to 0L,
            JobOutputConventions.writeSkippedKey to 0L,
            JobOutputConventions.writeBytesKey to 19_293_798L,
            JobOutputConventions.writeOpenKey to 1L,
            JobOutputConventions.writeOpenNameKey to "output.csv.gz"))
        val done = progress(mapOf(
            JobOutputConventions.writeFilesKey to 1L,
            JobOutputConventions.writeSkippedKey to 0L,
            JobOutputConventions.writeBytesKey to 44_249_908L,
            JobOutputConventions.writeOpenKey to 0L))

        assertEquals("output.csv.gz · 18.3 MB", OutputProgressText.write(writing))
        assertEquals("1 file · 42.2 MB", OutputProgressText.write(done))
    }


    @Test
    fun writeCountsSeveralOpenFilesAndSaysSkippedOnlyWhenThereAreAny() {
        val progress = progress(mapOf(
            JobOutputConventions.writeFilesKey to 2L,
            JobOutputConventions.writeSkippedKey to 1L,
            JobOutputConventions.writeBytesKey to 2048L,
            JobOutputConventions.writeOpenKey to 3L))

        assertEquals("2 files · 3 open · 2 kB · 1 skipped", OutputProgressText.write(progress))
    }


    @Test
    fun eachDiscardIsOneSentenceAtItsLocalTime() {
        val at = Date(2026, 9, 9, 11, 2).getTime().toLong()
        val later = Date(2026, 9, 9, 14, 30).getTime().toLong()
        val progress = progress(mapOf(
            JobOutputConventions.writeDiscardsKey to listOf(
                mapOf(JobOutputConventions.discardAtKey to at.toDouble(), JobOutputConventions.discardOutputsKey to 3L),
                mapOf(JobOutputConventions.discardAtKey to later, JobOutputConventions.discardOutputsKey to 1L),
                mapOf(JobOutputConventions.discardAtKey to later))))

        assertEquals(
            listOf(
                "An edit at 11:02 restarted 3 files; records before it are not in them.",
                "An edit at 14:30 restarted 1 file; records before it are not in it."),
            OutputProgressText.discards(progress))
        assertEquals(listOf(), OutputProgressText.discards(progress(mapOf())))
    }


    private fun progress(map: Map<String, Any?>): JobWorkerProgress =
        JobWorkerProgress(null, map)
}
