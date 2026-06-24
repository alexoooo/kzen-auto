package tech.kzen.auto.client.objects.document.job


/**
 * A Worker's live progress as shown in the Job UI: its lifecycle [status] (started / done / failed, from the
 * bare trace path), scalar [counts] (read / seen / kept / written / computed), and — for a PreviewWorker — a
 * sampled [header] + [rows] teaser and the total [rowCount]. Parsed from the structured progress map a Worker
 * publishes via `JobControl.publishProgress` (and, for the on-demand duplex query, from the PreviewWorker's
 * slice reply, which has the same shape).
 */
data class JobWorkerProgress(
    val status: String?,
    val counts: Map<String, String>,
    val header: List<String>,
    val rows: List<List<String>>,
    val rowCount: Long?
) {
    companion object {
        fun ofProgressMap(status: String?, raw: Any?): JobWorkerProgress {
            val map = raw as? Map<*, *>

            val counts = LinkedHashMap<String, String>()
            var header = listOf<String>()
            var rows = listOf<List<String>>()
            var rowCount: Long? = null

            if (map != null) {
                for ((rawKey, rawValue) in map) {
                    val key = rawKey as? String ?: continue
                    when (key) {
                        "header" ->
                            header = (rawValue as? List<*>)?.map { it.toString() } ?: listOf()

                        "rows" ->
                            rows = (rawValue as? List<*>)?.map { row ->
                                (row as? List<*>)?.map { it.toString() } ?: listOf()
                            } ?: listOf()

                        "count" ->
                            rowCount = toLong(rawValue)

                        // The duplex slice reply also carries its offset; not shown, ignore.
                        "offset" -> {}

                        else ->
                            counts[key] = rawValue?.toString() ?: ""
                    }
                }
            }

            return JobWorkerProgress(status, counts, header, rows, rowCount)
        }


        private fun toLong(value: Any?): Long? {
            return when (value) {
                is Long -> value
                is Int -> value.toLong()
                is Double -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
        }
    }
}
