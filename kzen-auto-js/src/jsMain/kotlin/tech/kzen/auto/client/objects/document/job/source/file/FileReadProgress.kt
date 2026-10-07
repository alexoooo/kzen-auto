package tech.kzen.auto.client.objects.document.job.source.file

import tech.kzen.auto.client.objects.document.common.file.FileRowStatus
import tech.kzen.auto.client.objects.document.job.JobWorkerProgress
import tech.kzen.auto.client.objects.document.job.WorkerOutcome
import tech.kzen.auto.common.objects.document.job.JobReadConventions


/**
 * How far a run has read a File Worker's files, from the File's own validation (which files, how large) and the
 * progress of whichever Worker reads them, through [JobReadConventions] alone — nothing here knows it is Parse.
 *
 * Files are read in selection order, one at a time: the file named by the reader's `readPath` is being read,
 * the ones before it are done and the ones after it pending. Between files no `readPath` is published, and the
 * reader's done count says where it is instead.
 */
object FileReadProgress {
    //-----------------------------------------------------------------------------------------------------------------
    /** One selected file: [location] as its selection row writes it, [path] as a reader names it. */
    data class ListedFile(
        val location: String,
        val path: String,
        val size: Long
    )


    data class Progress(
        val rows: Map<String, FileRowStatus>,
        val bytesRead: Long,
        val bytesTotal: Long
    ) {
        fun percent(): Int? =
            bytesTotal.takeIf { it > 0 }?.let { FileRowStatus.percentOf(bytesRead, it) }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** The files a File Worker's validation details list, in order; empty when there are none. */
    fun listedFiles(details: Map<String, Any?>): List<ListedFile> {
        val listed = details[JobReadConventions.filesKey] as? List<*>
            ?: return listOf()
        return listed.mapNotNull { file ->
            val fields = file as? Map<*, *> ?: return@mapNotNull null
            val location = fields[JobReadConventions.fileLocationKey] as? String ?: return@mapNotNull null
            val path = fields[JobReadConventions.filePathKey] as? String ?: return@mapNotNull null
            val size = JobWorkerProgress.longOf(fields[JobReadConventions.fileSizeKey]) ?: return@mapNotNull null
            ListedFile(location, path, size)
        }
    }


    /**
     * Each file's status by [ListedFile.location], and the bytes read across all of them; null before the reader
     * has reported anything, or when it publishes none of the read keys.
     */
    fun of(files: List<ListedFile>, reader: JobWorkerProgress?): Progress? {
        if (reader == null || files.isEmpty()) {
            return null
        }
        // A consumer that does not read the files (a Preview of the listing, say) says nothing about them
        val progress = reader.progressMap
        if (JobReadConventions.readDoneKey !in progress && JobReadConventions.readPathKey !in progress) {
            return null
        }

        val succeeded = reader.outcome?.kind == WorkerOutcome.Kind.Success
        val readPath = progress[JobReadConventions.readPathKey] as? String
        val readingIndex = readPath?.let { path -> files.indexOfFirst { it.path == path } } ?: -1
        val doneCount =
            if (readingIndex >= 0) {
                readingIndex
            }
            else {
                (reader.longValue(JobReadConventions.readDoneKey) ?: 0L).toInt()
            }

        val rows = linkedMapOf<String, FileRowStatus>()
        var bytesRead = 0L
        for ((index, file) in files.withIndex()) {
            val status = when {
                succeeded || index < doneCount -> FileRowStatus.Done
                index == readingIndex -> FileRowStatus.Reading(
                    reader.longValue(JobReadConventions.readBytesKey),
                    reader.longValue(JobReadConventions.readSizeKey) ?: file.size)
                else -> FileRowStatus.Pending
            }
            rows[file.location] = status
            bytesRead += when (status) {
                FileRowStatus.Done -> file.size
                is FileRowStatus.Reading -> (status.bytes ?: 0L).coerceIn(0L, file.size)
                FileRowStatus.Pending -> 0L
            }
        }
        return Progress(rows, bytesRead, files.sumOf { it.size })
    }
}
