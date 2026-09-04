package tech.kzen.auto.server.data.read.detection

import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.ContentCodingSpec


fun interface AutomaticFormatResolverObserver {
    fun completed(observation: AutomaticFormatResolutionObservation)


    companion object {
        val none = AutomaticFormatResolverObserver { }
    }
}


data class AutomaticFormatResolutionObservation(
    val ref: DataRef,
    val result: FormatResolutionResult,
    val cacheState: FormatDetectionCacheState,
    val decodedSampleBytes: Int,
    val acquisitionCodings: List<ContentCodingSpec>,
    val completeLogicalRecordsByCandidate: Map<String, Int>,
    val elapsedNanoseconds: Long
) {
    init {
        require(decodedSampleBytes >= 0)
        require(completeLogicalRecordsByCandidate.values.all { it >= 0 })
        require(elapsedNanoseconds >= 0)
    }
}


enum class FormatDetectionCacheState {
    Cold,
    WarmBeforeAcquisition,
    WarmAfterAcquisition
}
