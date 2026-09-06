package tech.kzen.auto.server.data.read.detection

import tech.kzen.auto.plugin.api.data.StrictCharacterView


/**
 * The bounded sample as text, when it is text. With no explicit encoding demanded and no text-only hint, a sample
 * that decodes under no permitted encoding yields no view and keeps the failure: probes still run over the bytes
 * (a binary reader matches on framing), and [textFailure] surfaces only when nothing structured matched.
 */
data class DecodedDetectionSample(
    val characterViews: List<StrictCharacterView>,
    val warning: String?,
    val textFailure: FormatDetectionException? = null
)
