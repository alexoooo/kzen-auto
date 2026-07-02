package tech.kzen.auto.client.objects.document.job.edit

import tech.kzen.auto.common.objects.document.job.JobChannelDerivation
import tech.kzen.auto.common.objects.document.job.JobServeCapability
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure


/**
 * Shared upstream-schema query for the summary-aware Job attribute editors (value-set filter, pivot): walk the
 * order-driven pipeline UPSTREAM from a Worker and return the first worker whose `serve` port is a `SummaryServer`,
 * whose live TableSummary (threaded via [tech.kzen.auto.client.objects.document.job.JobSummaryStore]) supplies the
 * editor's candidate columns / distinct values. A pure function of the saved structure — the same
 * [JobChannelDerivation] the gold pipes use — so the two cannot drift, and any worker that serves a summary
 * (not just the built-in SummaryWorker) qualifies (see [JobServeCapability]).
 */
object JobUpstreamSchema {
    fun nearestUpstreamSummaryWorker(graphStructure: GraphStructure, from: ObjectLocation): ObjectLocation? {
        val upstreamOf = JobChannelDerivation
            .derive(graphStructure, from.documentPath)
            .connections
            .associate { it.downstreamWorker to it.upstreamWorker }

        val visited = mutableSetOf<ObjectLocation>()
        var current = upstreamOf[from]
        while (current != null && visited.add(current)) {
            if (JobServeCapability.of(graphStructure, current) == JobServeCapability.Capability.Summary) {
                return current
            }
            current = upstreamOf[current]
        }
        return null
    }
}
