package tech.kzen.auto.client.objects.document.job.display.output

import tech.kzen.auto.client.objects.document.job.JobWorkerProgress
import tech.kzen.auto.common.objects.document.job.JobOutputConventions
import tech.kzen.auto.common.util.FormatUtils
import kotlin.js.Date


/** What `Format` and `Write` cards say about a run, from their [JobOutputConventions] progress. */
object OutputProgressText {
    //-----------------------------------------------------------------------------------------------------------------
    /**
     * "12,345 records · 3.1 MB", then, when records are grouped, "4 groups" and how many are open while only some
     * are; null before anything is encoded.
     */
    fun format(progress: JobWorkerProgress?): String? {
        if (progress == null) {
            return null
        }
        val parts = mutableListOf<String>()
        progress.status?.let { parts.add(it) }
        progress.longValue(JobOutputConventions.formatRecordsKey)?.let { parts.add(count(it, "record")) }
        progress.longValue(JobOutputConventions.formatBytesKey)?.takeIf { it > 0 }?.let {
            parts.add(FormatUtils.readableFileSize(it))
        }
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


    /** "output.csv.gz · 18.4 MB" while one file is open, "1 file · 42.2 MB" once published; null before Run. */
    fun write(progress: JobWorkerProgress?): String? {
        if (progress == null) {
            return null
        }
        val parts = mutableListOf<String>()
        progress.status?.let { parts.add(it) }
        progress.longValue(JobOutputConventions.writeFilesKey)?.takeIf { it > 0 }?.let { parts.add(count(it, "file")) }

        val open = progress.longValue(JobOutputConventions.writeOpenKey) ?: 0
        val openName = progress.progressMap[JobOutputConventions.writeOpenNameKey] as? String
        when {
            openName != null -> parts.add(openName)
            open > 0 -> parts.add("$open open")
        }

        progress.longValue(JobOutputConventions.writeBytesKey)?.takeIf { it > 0 }?.let {
            parts.add(FormatUtils.readableFileSize(it))
        }
        progress.longValue(JobOutputConventions.writeSkippedKey)?.takeIf { it > 0 }?.let {
            parts.add("${FormatUtils.decimalSeparator(it)} skipped")
        }
        return parts.joinToString(" · ").takeIf { parts.isNotEmpty() }
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
