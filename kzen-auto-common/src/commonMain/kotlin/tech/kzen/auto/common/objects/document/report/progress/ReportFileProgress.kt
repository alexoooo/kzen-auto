package tech.kzen.auto.common.objects.document.report.progress

import tech.kzen.auto.common.util.FormatUtils


data class ReportFileProgress(
    val running: Boolean,
    val finished: Boolean,
    val durationMillis: Long,
    val records: Long,
    val readBytes: Long,
    val uncompressedBytes: Long,
    val recentBytesPerSecond: Long
) {
    companion object {
        private const val runningKey: String = "running"
        private const val finishedKey: String = "finished"
        private const val durationMillisKey: String = "duration"
        private const val recordsKey: String = "records"
        private const val readBytesKey: String = "read"
        private const val uncompressedBytesKey: String = "uncompressed"
        private const val recentBytesPerSecondKey: String = "speed"


        fun fromCollection(collection: Map<String, Any>): ReportFileProgress {
            return ReportFileProgress(
                collection[runningKey] as Boolean,
                collection[finishedKey] as Boolean,
                (collection[durationMillisKey] as String).toLong(),
                (collection[recordsKey] as String).toLong(),
                (collection[readBytesKey] as String).toLong(),
                (collection[uncompressedBytesKey] as String).toLong(),
                (collection[recentBytesPerSecondKey] as String).toLong()
            )
        }
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
            runningKey to running,
            finishedKey to finished,
            durationMillisKey to durationMillis.toString(),
            recordsKey to records.toString(),
            readBytesKey to readBytes.toString(),
            uncompressedBytesKey to uncompressedBytes.toString(),
            recentBytesPerSecondKey to recentBytesPerSecond.toString()
        )
    }


    fun toMessage(totalSize: Long): String {
        if (! running && ! finished) {
            return ""
        }

        val recordsFormat = FormatUtils.decimalSeparator(records)

        val readFormat =
            if (readBytes != uncompressedBytes) {
                " (" + FormatUtils.readableFileSize(uncompressedBytes) + " uncompressed) "
            }
            else {
                ""
            }

        val durationSeconds = durationMillis / 1000

        return when {
            finished -> {
                val speed = FormatUtils.readableFileSize(1000L * uncompressedBytes / durationMillis)
                "Done: $recordsFormat records ${readFormat}took ${durationSeconds}s at $speed/s"
            }

            else -> {
                val percent = ((readBytes.toDouble() / totalSize) * 100).toInt()
                val speed = FormatUtils.readableFileSize(recentBytesPerSecond)
                "$percent%: $recordsFormat records ${readFormat}for ${durationSeconds}s at $speed/s"
            }
        }
    }
}