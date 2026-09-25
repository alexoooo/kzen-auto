package tech.kzen.auto.client.objects.document.job.edit

import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.objects.document.job.JobChannelDerivation
import tech.kzen.auto.common.objects.document.job.JobServeCapability
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure


/**
 * Shared upstream-schema query for the column-aware Job attribute editors (sort, value-set filter, pivot): the
 * columns a Worker's input carries. A live TableSummary of the nearest upstream worker whose `serve` port is a
 * `SummaryServer` (threaded via [tech.kzen.auto.client.objects.document.job.JobSummaryStore]) supplies them first,
 * then the server's validation of the immediate upstream Worker — its declared type, or the type validation read
 * from data before Run (docs/plans/2026-09-24_values-metadata-and-design-time-types.md, R5). The browser keeps no
 * copy of how data is typed. A pure function of the saved structure — the same [JobChannelDerivation] the gold
 * pipes use — so any worker that serves a summary (not just the built-in SummaryWorker) qualifies (see
 * [JobServeCapability]). [upstreamContract] is the same validated contract for the path pickers.
 */
object JobUpstreamSchema {
    enum class Provider {
        LiveSummary,
        Validated
    }


    sealed interface ContractResult {
        data class Available(val contract: DataContract): ContractResult
        data object Unavailable: ContractResult
        data class Error(val message: String): ContractResult
    }


    data class Result(
        val provider: Provider,
        val result: ContractResult
    ) {
        val contract: DataContract?
            get() = (result as? ContractResult.Available)?.contract

        /** Legacy editor boundary; the upstream walk itself remains contract-native. */
        val columns: HeaderListing?
            get() = contract?.let { LegacyDataShapeBridge.headerOrNull(
                DataShape(
                    it,
                    tech.kzen.lib.common.exec.data.shape.ShapeProvenance.Carried,
                    tech.kzen.lib.common.exec.data.shape.ShapeStability.Stable)) }
    }


    internal fun choose(
        liveSummary: DataContract?,
        validated: ContractResult?
    ): Result? = when {
        liveSummary != null -> Result(Provider.LiveSummary, ContractResult.Available(liveSummary))
        validated != null && validated != ContractResult.Unavailable -> Result(Provider.Validated, validated)
        else -> null
    }


    fun nearestUpstreamSummaryWorker(graphStructure: GraphStructure, from: ObjectLocation): ObjectLocation? {
        val upstreamOf = upstreamOf(graphStructure, from)
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


    /** Ordered providers: live Summary, the immediate upstream Worker's validated output, none. */
    fun columns(
        graphStructure: GraphStructure,
        from: ObjectLocation,
        summaries: Map<ObjectLocation, TableSummary>,
        validation: JobValidation?
    ): Result? {
        val summaryWorker = nearestUpstreamSummaryWorker(graphStructure, from)
        val summary = summaryWorker?.let(summaries::get)
        val liveSummary = summary?.let {
            LegacyDataShapeBridge.tabular(HeaderListing(it.columnSummaries.map.keys.toList())).itemType
        }
        val upstream = upstreamOf(graphStructure, from)[from]
        val validated = upstream?.let { validation?.workerValidations?.get(it.objectPath) }?.let { step ->
            val error = step.errorMessage
            val contract = step.contract
            when {
                error != null -> ContractResult.Error(error)
                contract == null || contract.structural is DataType.Dynamic -> ContractResult.Unavailable
                else -> ContractResult.Available(contract)
            }
        }
        return choose(liveSummary, validated)
    }


    /**
     * The immediate upstream Worker's validated output contract, for the path pickers (Paths, writer columns),
     * or why there is none.
     */
    fun upstreamContract(
        graphStructure: GraphStructure,
        from: ObjectLocation,
        validation: JobValidation?
    ): Pair<DataContract?, String?> {
        val upstream = upstreamOf(graphStructure, from)[from]
            ?: return null to "No upstream Worker: connect an input first"
        val stepValidation = validation?.workerValidations?.get(upstream.objectPath)
            ?: return null to "Upstream contract not available yet"
        val contract = stepValidation.contract
            ?: return null to "Upstream contract unknown (${upstream.objectPath.name.value})"
        if (contract.structural is DataType.Dynamic) {
            return null to "Upstream contract is dynamic: paths bind at run time"
        }
        return contract to null
    }


    private fun upstreamOf(graphStructure: GraphStructure, from: ObjectLocation): Map<ObjectLocation, ObjectLocation> =
        JobChannelDerivation
            .derive(graphStructure, from.documentPath)
            .connections
            .associate { it.downstreamWorker to it.upstreamWorker }
}
