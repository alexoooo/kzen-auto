package tech.kzen.auto.server.data.read.detection

import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.DataContentFingerprint


data class AcquiredDetectionSample(
    val bytes: ByteArray,
    val endOfInput: Boolean,
    val coding: ContentCodingSpec,
    val acquisitionCodings: List<ContentCodingSpec>,
    val observedFingerprint: DataContentFingerprint
)
