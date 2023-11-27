package tech.kzen.auto.common.paradigm.logic.run.model

import kotlinx.datetime.Instant


data class LogicStatus(
    val time: Instant,
    val active: LogicRunInfo?
) {
    companion object {
        private const val timeKey = "time"
        private const val activeKey = "active"

        fun ofCollection(collection: Map<String, Any>): LogicStatus {
            val active = when (
                val activeValue = collection[activeKey]!!
            ) {
                "null" ->
                    null

                else ->
                    @Suppress("UNCHECKED_CAST")
                    (LogicRunInfo.ofCollection(activeValue as Map<String, Any>))
            }

            return LogicStatus(
                Instant.parse(collection[timeKey] as String),
                active)
        }
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
            timeKey to time.toString(),
            activeKey to (active?.toCollection() ?: "null"))
    }
}
