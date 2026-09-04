package tech.kzen.auto.server.data.format


data class SourceFormatResolutionPolicy(
    val maximumConcurrentColdParts: Int = defaultMaximumConcurrentColdParts,
    val maximumColdParts: Int = defaultMaximumColdParts,
    val maximumDecodedBytes: Long = defaultMaximumDecodedBytes,
    val overallTimeoutMillis: Long = defaultOverallTimeoutMillis
) {
    init {
        require(maximumConcurrentColdParts > 0) { "Concurrent cold-part limit must be positive" }
        require(maximumColdParts > 0) { "Cold-part limit must be positive" }
        require(maximumDecodedBytes > 0) { "Decoded-byte limit must be positive" }
        require(overallTimeoutMillis > 0) { "Source-resolution timeout must be positive" }
    }

    companion object {
        const val defaultMaximumConcurrentColdParts = 4
        const val defaultMaximumColdParts = 256
        const val defaultMaximumDecodedBytes = 64L * 1024 * 1024
        const val defaultOverallTimeoutMillis = 15_000L

        val default = SourceFormatResolutionPolicy()
    }
}
