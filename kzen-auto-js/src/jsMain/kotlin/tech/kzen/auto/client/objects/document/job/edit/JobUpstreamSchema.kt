package tech.kzen.auto.client.objects.document.job.edit

import tech.kzen.auto.common.objects.document.job.JobChannelDerivation
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * Shared upstream-schema query for the summary-aware Job attribute editors (value-set filter, pivot): walk the
 * order-driven pipeline UPSTREAM from a Worker and return the first SummaryWorker, whose live TableSummary
 * (threaded via [tech.kzen.auto.client.objects.document.job.JobSummaryStore]) supplies the editor's candidate
 * columns / distinct values. A pure function of the saved structure — the same [JobChannelDerivation] the gold
 * pipes use — so the two cannot drift, and there's one place to teach about other schema-bearing upstreams later.
 */
object JobUpstreamSchema {
    fun nearestUpstreamSummaryWorker(graphStructure: GraphStructure, from: ObjectLocation): ObjectLocation? {
        val upstreamOf = JobChannelDerivation
            .derive(graphStructure, from.documentPath)
            .connections
            .associate { it.downstreamWorker to it.upstreamWorker }

        val graphNotation = graphStructure.graphNotation
        val visited = mutableSetOf<ObjectLocation>()
        var current = upstreamOf[from]
        while (current != null && visited.add(current)) {
            if (isSummaryWorker(graphNotation, current)) {
                return current
            }
            current = upstreamOf[current]
        }
        return null
    }


    private fun isSummaryWorker(graphNotation: GraphNotation, workerLocation: ObjectLocation): Boolean {
        return graphNotation
            .firstAttribute(workerLocation, AttributePath.ofName(NotationConventions.isAttributeName))
            ?.asString() == "SummaryWorker"
    }
}
