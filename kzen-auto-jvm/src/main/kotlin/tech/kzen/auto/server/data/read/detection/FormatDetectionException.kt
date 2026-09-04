package tech.kzen.auto.server.data.read.detection

import tech.kzen.auto.common.data.format.detection.FormatDetectionFailureCategory


class FormatDetectionException(
    val category: FormatDetectionFailureCategory,
    message: String,
    cause: Throwable? = null
): IllegalArgumentException(message, cause)
