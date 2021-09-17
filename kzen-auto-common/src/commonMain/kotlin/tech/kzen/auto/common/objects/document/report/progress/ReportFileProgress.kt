package tech.kzen.auto.common.objects.document.report.progress

import tech.kzen.auto.common.util.FormatUtils


data class ReportFileProgress(
    val running: Boolean,
    val finished: Boolean,
    val durationMillis: Long,
    val records: Long,
    val readBytes: Long,
    val uncompressedBytes: Long,
    val recentBytesPerSecond: Long,
    val recentRecordsPerSecond: Long
) {
    companion object {
        private const val runningKey: String = "running"
        private const val finishedKey: String = "finished"
        private const val durationMillisKey: String = "duration"
        private const val recordsKey: String = "records"
        private const val readBytesKey: String = "read"
        private const val uncompressedBytesKey: String = "uncompressed"
        private const val recentBytesPerSecondKey: String = "speedBytes"
        private const val recentRecordsPerSecondKey: String = "speedRecords"


        fun fromCollection(collection: Map<String, Any>): ReportFileProgress {
            return ReportFileProgress(
                collection[runningKey] as Boolean,
                collection[finishedKey] as Boolean,
                (collection[durationMillisKey] as String).toLong(),
                (collection[recordsKey] as String).toLong(),
                (collection[readBytesKey] as String).toLong(),
                (collection[uncompressedBytesKey] as String).toLong(),
                (collection[recentBytesPerSecondKey] as String).toLong(),
                (collection[recentRecordsPerSecondKey] as String).toLong()
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
            recentBytesPerSecondKey to recentBytesPerSecond.toString(),
            recentRecordsPerSecondKey to recentRecordsPerSecond.toString()
        )
    }


    fun toMessage(totalSize: Long): String {
//        if (! running && ! finished) {
//            return ""
//        }

        val recordsFormat = FormatUtils.decimalSeparator(records)

        val readFormat =
            if (readBytes != uncompressedBytes) {
                FormatUtils.readableFileSize(uncompressedBytes) + " uncompressed"
            }
            else {
                FormatUtils.readableFileSize(readBytes)
            }

        val adjustedDurationMillis = durationMillis.coerceAtLeast(1)
        val durationSeconds = adjustedDurationMillis / 1000

        return when {
            finished -> {
                val overallRecordsSpeed = FormatUtils.decimalSeparator(
                    1000L * records / adjustedDurationMillis)

                val overallDataSpeed = FormatUtils.readableFileSize(
                    1000L * uncompressedBytes / adjustedDurationMillis)

                "Done: $recordsFormat records (${readFormat}) took ${durationSeconds}s " +
                        "at $overallRecordsSpeed/s ($overallDataSpeed/s)"
            }

            else -> {
                val percent = ((readBytes.toDouble() / totalSize.coerceAtLeast(1)) * 100)
                val percentFormat = percent.toInt().toString() + "." + (percent * 10 % 10).toInt()

                val recentRecordsSpeed = FormatUtils.decimalSeparator(recentRecordsPerSecond)
                val recentDataSpeed = FormatUtils.readableFileSize(recentBytesPerSecond)

                "$percentFormat%: $recordsFormat records (${readFormat}) for ${durationSeconds}s " +
                        "at $recentRecordsSpeed/s ($recentDataSpeed/s)"
            }
        }
    }
}