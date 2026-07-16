package tech.kzen.auto.client.objects.document.job


/**
 * A Worker's live progress as shown in the Job UI: its lifecycle [status] (started / done / failed, from the
 * bare trace path) and the opaque [progressMap] the Worker published via `JobControl.publishProgress` (and, for
 * the on-demand duplex query, the Worker's serve reply, which has the same shape).
 *
 * This type is deliberately schema-agnostic — the mirror of the server's opaque `Map<String, Any?>` progress
 * contract (`WorkerBase.progress`). It knows nothing about any specific Worker's payload keys: a per-type
 * `WorkerDisplay` parses its own keys out of [progressMap] (e.g. PreviewWorkerDisplay reads "header"/"rows",
 * SummaryWorkerDisplay reads "summary"), and the default card renders only [status] plus the generic scalar
 * entries. Do NOT add a Worker-specific field here — that reintroduces the god object and breaks 3rd-party
 * extensibility.
 *
 * [outcome] is the exception that proves the rule: a terminal outcome is a GENERAL per-node fact (every Worker
 * settles with one), like [status], not a Worker-type payload — so it is a typed field, rendered uniformly by
 * the default card, with no per-Worker branching.
 */
data class JobWorkerProgress(
    val status: String?,
    val progressMap: Map<String, Any?>,
    val outcome: WorkerOutcome? = null
) {
    // Generic numeric coercion of one progress key — no Worker schema named here. Shared by the displays that
    // read the conventional "count" key (over the wire an integer can arrive as Long / Int / Double / String).
    fun longValue(key: String): Long? {
        return when (val value = progressMap[key]) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }


    companion object {
        fun ofProgressMap(status: String?, raw: Any?, outcomeRaw: Any? = null): JobWorkerProgress {
            val map = raw as? Map<*, *>

            val progressMap = LinkedHashMap<String, Any?>()
            if (map != null) {
                for ((rawKey, rawValue) in map) {
                    val key = rawKey as? String ?: continue
                    progressMap[key] = rawValue
                }
            }

            return JobWorkerProgress(status, progressMap, WorkerOutcome.ofTraceValue(outcomeRaw))
        }
    }
}
