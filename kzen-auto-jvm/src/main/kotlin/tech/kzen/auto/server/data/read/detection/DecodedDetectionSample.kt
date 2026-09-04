package tech.kzen.auto.server.data.read.detection

import tech.kzen.auto.plugin.api.data.StrictCharacterView


data class DecodedDetectionSample(
    val characterViews: List<StrictCharacterView>,
    val warning: String?
)
