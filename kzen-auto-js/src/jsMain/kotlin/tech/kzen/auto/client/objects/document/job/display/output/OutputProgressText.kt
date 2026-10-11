package tech.kzen.auto.client.objects.document.job.display.output

import tech.kzen.auto.client.objects.document.job.JobWorkerProgress
import tech.kzen.auto.client.objects.document.job.display.ProgressRate
import tech.kzen.auto.common.objects.document.job.JobOutputConventions
import tech.kzen.auto.common.util.FormatUtils
import kotlin.js.Date


/** What `Format` and `Write` cards say about a run, from their [JobOutputConventions] progress. */
object OutputProgressText {
    //-----------------------------------------------------------------------------------------------------------------
    /** A file `Write` is writing ([open]) or has published, by absolute [path], with its bytes on disk so far. */
    data class WriteFile(
        val path: String,
        val bytes: Long,
        val open: Boolean
    )


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * "12,345 records · 3.1 MB · 850,000 records/s" ([recordRate] measures the records), then, when records are
     * grouped, "4 groups" and how many are open while only some are; null before anything is encoded.
     */
    fun format(progress: JobWorkerProgress?, recordRate: ProgressRate? = null): String? {
        if (progress == null) {
            return null
        }
        val parts = mutableListOf<String>()
        progress.status?.let { parts.add(it) }
        val records = progress.longValue(JobOutputConventions.formatRecordsKey)
        records?.let { parts.add(count(it, "record")) }
        progress.longValue(JobOutputConventions.formatBytesKey)?.takeIf { it > 0 }?.let {
            parts.add(FormatUtils.readableFileSize(it))
        }
        recordRate?.perSecond(progress, records)?.let { parts.add(ProgressRate.countText(it, "records")) }
        val open = progress.longValue(JobOutputConventions.formatGroupsOpenKey)
        val done = progress.longValue(JobOutputConventions.formatGroupsDoneKey)
        if (open != null && done != null && open + done > 0) {
            parts.add(count(open + done, "group"))
            if (open in 1 until open + done) {
                parts.add("$open open")
            }
        }
        return parts.joinToString(" · ").takeIf { parts.isNotEmpty() }
    }


    /**
     * "C:\data\output.csv.gz · 18.4 MB · 12.1 MB/s" while the run's only file is written or once it is published
     * ([byteRate] measures the bytes on disk), "3 files · 2 open · 42.2 MB" when there are more; null before Run.
     */
    fun write(progress: JobWorkerProgress?, byteRate: ProgressRate? = null): String? {
        if (progress == null) {
            return null
        }
        val parts = mutableListOf<String>()
        progress.status?.let { parts.add(it) }

        val published = progress.longValue(JobOutputConventions.writeFilesKey) ?: 0
        val open = progress.longValue(JobOutputConventions.writeOpenKey) ?: 0
        val only = writeFiles(progress).singleOrNull()?.takeIf { published + open == 1L }
        if (only != null) {
            parts.add(only.path)
        }
        else {
            if (published > 0) {
                parts.add(count(published, "file"))
            }
            if (open > 0) {
                parts.add("$open open")
            }
        }

        val bytes = progress.longValue(JobOutputConventions.writeBytesKey)
        bytes?.takeIf { it > 0 }?.let {
            parts.add(FormatUtils.readableFileSize(it))
        }
        byteRate?.perSecond(progress, bytes)?.let { parts.add(ProgressRate.bytesText(it)) }
        progress.longValue(JobOutputConventions.writeSkippedKey)?.takeIf { it > 0 }?.let {
            parts.add("${FormatUtils.decimalSeparator(it)} skipped")
        }
        return parts.joinToString(" · ").takeIf { parts.isNotEmpty() }
    }


    /**
     * The files `Write` lists: the ones it is writing, then the ones it published (while it runs, only the first
     * few open and the latest few published, the bounded teaser; once it is done, every published file).
     */
    fun writeFiles(progress: JobWorkerProgress?): List<WriteFile> {
        if (progress == null) {
            return listOf()
        }
        fun files(key: String, open: Boolean): List<WriteFile> =
            (progress.progressMap[key] as? List<*>).orEmpty().mapNotNull { file ->
                val fields = file as? Map<*, *> ?: return@mapNotNull null
                val path = fields[JobOutputConventions.filePathKey] as? String ?: return@mapNotNull null
                val bytes = JobWorkerProgress.longOf(fields[JobOutputConventions.fileBytesKey]) ?: 0L
                WriteFile(path, bytes, open)
            }
        return files(JobOutputConventions.writeOpenFilesKey, true) +
            files(JobOutputConventions.writeWrittenFilesKey, false)
    }


    /** One sentence per live edit that discarded open outputs, at the browser's local time. */
    fun discards(progress: JobWorkerProgress?): List<String> {
        val discards = progress?.progressMap?.get(JobOutputConventions.writeDiscardsKey) as? List<*>
            ?: return listOf()
        return discards.mapNotNull { discard ->
            val fields = discard as? Map<*, *> ?: return@mapNotNull null
            val atMillis = JobWorkerProgress.longOf(fields[JobOutputConventions.discardAtKey])
                ?: return@mapNotNull null
            val outputs = JobWorkerProgress.longOf(fields[JobOutputConventions.discardOutputsKey])
                ?: return@mapNotNull null
            val at = Date(atMillis.toDouble())
            val time = "${at.getHours().toString().padStart(2, '0')}:${at.getMinutes().toString().padStart(2, '0')}"
            val them = if (outputs == 1L) "it" else "them"
            "An edit at $time restarted ${count(outputs, "file")}; records before it are not in $them."
        }
    }


    private fun count(value: Long, noun: String): String =
        "${FormatUtils.decimalSeparator(value)} $noun${if (value == 1L) "" else "s"}"
}
