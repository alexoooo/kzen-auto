package tech.kzen.auto.common.data.format.detection


enum class FormatDetectionFailureCategory {
    Acquisition,
    FingerprintMismatch,
    ReadLimit,
    Timeout,
    Cancellation,
    Resolution;

    val cacheable: Boolean
        get() = false
}
