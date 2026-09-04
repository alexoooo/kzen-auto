package tech.kzen.auto.plugin.api.data


sealed interface ReaderProbeResult {
    data object NoMatch: ReaderProbeResult

    data class Rejected(
        val reason: String
    ): ReaderProbeResult {
        init {
            require(reason.isNotBlank()) { "Probe rejection reason must not be blank" }
        }
    }

    data class Matched(
        val strength: ReaderProbeStrength,
        val canonicalConfig: tech.kzen.auto.common.data.read.ReaderConfig,
        val evidence: String
    ): ReaderProbeResult {
        init {
            require(evidence.isNotBlank()) { "Probe match evidence must not be blank" }
        }
    }
}
