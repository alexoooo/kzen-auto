package tech.kzen.auto.plugin.api.data

import tech.kzen.auto.common.data.format.detection.DetectionPolicy
import tech.kzen.auto.common.data.format.detection.NormalizedFormatHints
import tech.kzen.auto.common.data.read.ReaderConfig
import tech.kzen.lib.common.util.ImmutableByteArray


data class ReaderProbeRequest(
    val candidateConfig: ReaderConfig,
    val hints: NormalizedFormatHints,
    val sample: ImmutableByteArray,
    val characterViews: List<StrictCharacterView>,
    val endOfInput: Boolean,
    val policy: DetectionPolicy,
    val structuredHint: Boolean = false,
    val exactExtension: Boolean = false,
    val allowCanonicalAdjustments: Boolean = false,
    val observer: ReaderProbeObserver = ReaderProbeObserver.none
) {
    init {
        require(characterViews.map(StrictCharacterView::encoding).distinct().size == characterViews.size) {
            "Probe character-view encodings must be unique"
        }
        require(!exactExtension || structuredHint) {
            "Exact extension validation requires a structured hint"
        }
    }
}
