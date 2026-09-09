package tech.kzen.auto.common.paradigm.logic

import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath

/** Elapsed time belongs to the whole run, including pauses and waits. It is independent of trace versions. */
data class RunTiming(val runId: String, val elapsedMillis: Long, val settled: Boolean) {
    fun toCollection(): Map<String, Any> = mapOf(
        "runId" to runId, "elapsedMillis" to elapsedMillis.toString(), "settled" to settled)

    companion object {
        val path = LogicTracePath(listOf("\$run-timing"))

        fun ofCollection(value: Any?): RunTiming? {
            val map = value as? Map<*, *> ?: return null
            return RunTiming(map["runId"] as? String ?: return null,
                map["elapsedMillis"]?.toString()?.toLongOrNull() ?: return null,
                map["settled"] as? Boolean ?: return null)
        }
    }
}
