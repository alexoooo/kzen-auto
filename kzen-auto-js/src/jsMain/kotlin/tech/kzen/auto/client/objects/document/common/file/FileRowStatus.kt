package tech.kzen.auto.client.objects.document.common.file


/**
 * Where a run is with one selected file, as [FileSelectionTable] shows it: not reached yet, being read, or read.
 * Whoever knows (a Job reading the selection) supplies it; the table only draws it.
 */
sealed interface FileRowStatus {
    data object Pending: FileRowStatus
    data object Done: FileRowStatus

    /** [bytes] is null when the reader cannot count them (it still names the file it is on). */
    data class Reading(val bytes: Long?, val size: Long): FileRowStatus {
        fun percent(): Int? =
            bytes?.let { percentOf(it, size) }
    }


    companion object {
        fun percentOf(part: Long, whole: Long): Int =
            if (whole <= 0) 100 else (part.coerceIn(0L, whole) * 100 / whole).toInt()
    }
}
