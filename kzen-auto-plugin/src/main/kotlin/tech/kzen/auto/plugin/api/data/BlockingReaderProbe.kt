package tech.kzen.auto.plugin.api.data


/**
 * The ordinary-Java face of [ReaderProbeCapability]: a probe written as a plain method over the sample bytes.
 * The suspend contract is bridged here, so a Java reader implements [probeBlocking] (and
 * [readerCompatibility]) and never sees a Continuation — the [BlockingReaderCapability] rule applied to
 * automatic format detection. A probe reads a bounded in-memory sample and must return promptly; it is not
 * offloaded, so it must not block on anything but its own decoding.
 */
interface BlockingReaderProbe: ReaderProbeCapability {
    fun probeBlocking(request: ReaderProbeRequest): ReaderProbeResult

    override suspend fun probe(request: ReaderProbeRequest): ReaderProbeResult = probeBlocking(request)
}
