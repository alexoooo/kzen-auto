package tech.kzen.auto.server.data.read

import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.format.detection.FormatDetectionCacheKey


class SuccessfulFormatResolutionCache(
    private val maximumEntries: Int = defaultMaximumEntries
) {
    init {
        require(maximumEntries > 0) { "Format-resolution cache size must be positive" }
    }

    private val entries = object: LinkedHashMap<FormatDetectionCacheKey, FormatResolutionResult>(
        maximumEntries.coerceAtMost(initialCapacity), loadFactor, true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<FormatDetectionCacheKey, FormatResolutionResult>
        ): Boolean = size > maximumEntries
    }

    @Synchronized
    fun get(key: FormatDetectionCacheKey): FormatResolutionResult? = entries[key]

    @Synchronized
    fun put(key: FormatDetectionCacheKey, result: FormatResolutionResult) {
        entries[key] = result
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    companion object {
        const val defaultMaximumEntries = 512
        private const val initialCapacity = 16
        private const val loadFactor = 0.75f
    }
}
