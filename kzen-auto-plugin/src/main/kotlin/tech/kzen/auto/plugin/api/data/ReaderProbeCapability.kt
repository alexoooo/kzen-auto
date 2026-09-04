package tech.kzen.auto.plugin.api.data


interface ReaderProbeCapability {
    val readerCompatibility: String

    suspend fun probe(request: ReaderProbeRequest): ReaderProbeResult
}
