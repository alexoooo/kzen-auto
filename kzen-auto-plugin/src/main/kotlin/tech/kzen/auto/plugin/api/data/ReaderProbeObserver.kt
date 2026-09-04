package tech.kzen.auto.plugin.api.data


/**
 * Optional measurement hook supplied by the format resolver. A probe reports each batch of complete logical
 * records it actually considered. Counts are non-negative and additive, so a probe may report once per parsing
 * attempt. Their sum must not exceed the request's logical-record limit. Probes must call the observer before
 * [ReaderProbeCapability.probe] returns.
 */
fun interface ReaderProbeObserver {
    fun completeLogicalRecordsConsidered(count: Int)


    companion object {
        val none = ReaderProbeObserver { }
    }
}
