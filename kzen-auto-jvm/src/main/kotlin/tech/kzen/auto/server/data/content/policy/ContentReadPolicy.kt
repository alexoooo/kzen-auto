package tech.kzen.auto.server.data.content.policy

import tech.kzen.auto.common.data.read.ReadOperationalPolicy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


data class ContentReadPolicy(
    val maximumExpandedBytes: Long,
    val timeout: Duration,
    val inspectionRecordLimit: Long
) {
    companion object {
        /** An absent expanded-byte or timeout limit is unbounded: a full run is ended by cancellation instead. */
        fun of(policy: ReadOperationalPolicy, inspectionRecordLimit: Long): ContentReadPolicy =
            ContentReadPolicy(
                policy.maximumExpandedBytes ?: Long.MAX_VALUE,
                policy.timeoutMillis?.milliseconds ?: Duration.INFINITE,
                inspectionRecordLimit)
    }


    init {
        require(maximumExpandedBytes >= 0)
        require(timeout.isPositive())
        require(inspectionRecordLimit >= 0)
    }
}
