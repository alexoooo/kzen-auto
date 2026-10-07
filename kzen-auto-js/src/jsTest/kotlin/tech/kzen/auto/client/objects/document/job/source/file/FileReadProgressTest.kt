package tech.kzen.auto.client.objects.document.job.source.file

import tech.kzen.auto.client.objects.document.common.file.FileRowStatus
import tech.kzen.auto.client.objects.document.job.JobWorkerProgress
import tech.kzen.auto.client.objects.document.job.WorkerOutcome
import tech.kzen.auto.common.objects.document.job.JobReadConventions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class FileReadProgressTest {
    private val files = listOf(
        FileReadProgress.ListedFile("C:/data/a.csv", "C:\\data\\a.csv", 100),
        FileReadProgress.ListedFile("C:/data/b.csv", "C:\\data\\b.csv", 300),
        FileReadProgress.ListedFile("C:/data/c.csv", "C:\\data\\c.csv", 600))


    @Test
    fun listedFilesReadTheValidationDetailsAndSkipMalformedEntries() {
        val details = mapOf(JobReadConventions.filesKey to listOf(
            mapOf(
                JobReadConventions.fileLocationKey to "C:/data/a.csv",
                JobReadConventions.filePathKey to "C:\\data\\a.csv",
                JobReadConventions.fileSizeKey to 100.0),
            mapOf(JobReadConventions.fileLocationKey to "missing-path")))

        assertEquals(listOf(files.first()), FileReadProgress.listedFiles(details))
        assertEquals(listOf(), FileReadProgress.listedFiles(mapOf()))
    }


    @Test
    fun theFileBeingReadSplitsTheSelectionIntoDoneAndPendingWeighedByBytes() {
        val progress = FileReadProgress.of(files, reader(mapOf(
            JobReadConventions.readDoneKey to 1L,
            JobReadConventions.readPathKey to "C:\\data\\b.csv",
            JobReadConventions.readBytesKey to 150L,
            JobReadConventions.readSizeKey to 300L)))!!

        assertEquals(FileRowStatus.Done, progress.rows["C:/data/a.csv"])
        assertEquals(FileRowStatus.Reading(150, 300), progress.rows["C:/data/b.csv"])
        assertEquals(50, (progress.rows["C:/data/b.csv"] as FileRowStatus.Reading).percent())
        assertEquals(FileRowStatus.Pending, progress.rows["C:/data/c.csv"])
        assertEquals(250, progress.bytesRead)
        assertEquals(1000, progress.bytesTotal)
        assertEquals(25, progress.percent())
    }


    @Test
    fun betweenFilesTheDoneCountPlacesTheReaderAndSuccessFinishesEveryRow() {
        val between = FileReadProgress.of(files, reader(mapOf(JobReadConventions.readDoneKey to 2L)))!!
        assertEquals(
            listOf(FileRowStatus.Done, FileRowStatus.Done, FileRowStatus.Pending),
            between.rows.values.toList())
        assertEquals(40, between.percent())

        val succeeded = FileReadProgress.of(
            files, reader(mapOf(JobReadConventions.readDoneKey to 3L), WorkerOutcome.Kind.Success))!!
        assertEquals(setOf(FileRowStatus.Done), succeeded.rows.values.toSet())
        assertEquals(100, succeeded.percent())
    }


    @Test
    fun aConsumerPublishingNoReadKeysSaysNothingAboutTheFiles() {
        assertNull(FileReadProgress.of(files, null))
        assertNull(FileReadProgress.of(files, reader(mapOf("count" to 3L))))
        assertNull(FileReadProgress.of(listOf(), reader(mapOf(JobReadConventions.readDoneKey to 0L))))
    }


    private fun reader(progress: Map<String, Any?>, outcome: WorkerOutcome.Kind? = null): JobWorkerProgress =
        JobWorkerProgress(null, progress, outcome?.let { WorkerOutcome(it, null) })
}
