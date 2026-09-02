package tech.kzen.auto.server.data.content.policy

import kotlin.time.Duration


data class ContentReadPolicy(
    val maximumExpandedBytes: Long,
    val timeout: Duration,
    val inspectionRecordLimit: Long
) {
    init {
        require(maximumExpandedBytes >= 0)
        require(timeout.isPositive())
        require(inspectionRecordLimit >= 0)
    }
}
