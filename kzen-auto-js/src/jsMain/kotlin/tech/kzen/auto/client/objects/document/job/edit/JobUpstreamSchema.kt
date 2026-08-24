package tech.kzen.auto.client.objects.document.job.edit

import tech.kzen.auto.client.objects.document.job.source.DataSourceResolveStore
import tech.kzen.auto.client.objects.document.job.source.DataSourceShapeStore
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.auto.common.data.model.DataManifest
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderLabel
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.job.JobChannelDerivation
import tech.kzen.auto.common.objects.document.job.JobServeCapability
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
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
    enum class Provider {
        LiveSummary,
        InspectedSource
    }


    data class Result(
        val provider: Provider,
        val columns: HeaderListing
    )


    internal data class ReadProjectionConfig(
        val source: ObjectLocation,
        val emit: String,
        val role: String,
        val attributes: String,
        val schemaMode: String
    )


    internal fun choose(
        liveSummary: HeaderListing?,
        inspectedSource: HeaderListing?
    ): Result? = when {
        liveSummary != null -> Result(Provider.LiveSummary, liveSummary)
        inspectedSource != null -> Result(Provider.InspectedSource, inspectedSource)
        else -> null
    }


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


    /** Ordered providers: live Summary, reserved J4 transformed-lane slot, explicitly inspected source, none. */
    fun columns(
        graphStructure: GraphStructure,
        from: ObjectLocation,
        summaries: Map<ObjectLocation, TableSummary>,
        resolveStore: DataSourceResolveStore?,
        shapeStore: DataSourceShapeStore?
    ): Result? {
        val upstreamOf = JobChannelDerivation
            .derive(graphStructure, from.documentPath)
            .connections
            .associate { it.downstreamWorker to it.upstreamWorker }
        val visited = mutableSetOf<ObjectLocation>()
        var current = upstreamOf[from]
        while (current != null && visited.add(current)) {
            if (JobServeCapability.of(graphStructure, current) == JobServeCapability.Capability.Summary) {
                val summary = summaries[current]
                if (summary != null) {
                    return choose(
                        HeaderListing(summary.columnSummaries.map.keys.toList()), null)
                }
            }

            // Reserved J4 provider slot: transformed-lane schemas will be consulted here before raw sources.

            val config = readProjectionConfig(graphStructure, current)
            if (config != null && resolveStore != null && shapeStore != null) {
                val manifest = resolveStore.state(config.source)?.result?.manifest
                if (manifest != null) {
                    val inspected = shapeStore.state(
                        DataSourceShapeStore.Key.of(config.source, manifest))
                    if (inspected != null) {
                        val columns = ReadShapeProjection.project(
                            manifest,
                            inspected.parts.mapValues { (_, state) ->
                                state.shape.takeIf { !state.inspecting && state.error == null }
                            },
                            config)
                        if (columns != null) {
                            return choose(null, columns)
                        }
                    }
                }
            }
            current = upstreamOf[current]
        }
        return null
    }


    internal fun projectInspectedLane(
        graphStructure: GraphStructure,
        provider: ObjectLocation,
        manifest: DataManifest,
        shapes: Map<DataPart, DataShape?>
    ): HeaderListing? {
        val config = readProjectionConfig(graphStructure, provider) ?: return null
        return ReadShapeProjection.project(manifest, shapes, config)
    }


    internal fun readProjectionConfig(
        graphStructure: GraphStructure,
        host: ObjectLocation
    ): ReadProjectionConfig? {
        if (!DataSourceConventions.isShapeProvider(graphStructure.graphNotation, host)) {
            return null
        }
        val metadata = graphStructure.graphMetadata.objectMetadata.map[host]
            ?: return null
        val notation = graphStructure.graphNotation
        val sourceMetadata = metadata.attributes.map[DataSourceConventions.shapeSourceAttributeName]
        if (!DataSourceConventions.isDataSourceType(sourceMetadata?.type)) {
            return null
        }
        val sourceValue = try {
            notation.firstAttribute(host, DataSourceConventions.shapeSourceAttributeName).asString()
        }
        catch (_: Exception) {
            null
        }
        if (sourceValue.isNullOrBlank()) {
            return null
        }
        val source = try {
            notation.coalesce.locateOptional(
                ObjectReference.parse(sourceValue), ObjectReferenceHost.ofLocation(host))
        }
        catch (_: Exception) {
            null
        } ?: return null

        fun setting(attributeName: tech.kzen.lib.common.model.attribute.AttributeName): String? {
            return try {
                notation.firstAttribute(host, attributeName).asString()
            }
            catch (_: Exception) {
                null
            }
        }

        return ReadProjectionConfig(
            source,
            setting(DataSourceConventions.shapeEmitAttributeName) ?: return null,
            setting(DataSourceConventions.shapeRoleAttributeName) ?: return null,
            setting(DataSourceConventions.shapeAttributesAttributeName) ?: return null,
            setting(DataSourceConventions.shapeSchemaModeAttributeName) ?: return null)
    }
}


/** Pure client projection of inspected part shapes through the conventional DataSourceShapeProvider Read lane. */
internal object ReadShapeProjection {
    private const val emitItems = "items"
    private const val attributesIgnore = "ignore"
    private const val attributesColumns = "columns"
    private const val schemaStrict = "strict"
    private const val schemaSuperset = "superset"


    fun project(
        manifest: DataManifest,
        shapes: Map<DataPart, DataShape?>,
        config: JobUpstreamSchema.ReadProjectionConfig
    ): HeaderListing? {
        if (config.emit != emitItems ||
            (config.attributes != attributesIgnore && config.attributes != attributesColumns) ||
            (config.schemaMode != schemaStrict && config.schemaMode != schemaSuperset)) {
            return null
        }

        val candidates = mutableListOf<Candidate>()
        for (unit in manifest.units) {
            val selected = if (config.role.isNotBlank()) {
                unit.partsOf(DataRole(config.role)).takeIf { it.isNotEmpty() }
            }
            else {
                val roles = unit.parts.map { it.role }.distinct()
                roles.singleOrNull()?.let(unit::partsOf)
            } ?: return null

            val attributes = if (config.attributes == attributesColumns) unit.attributes else null
            for (part in selected) {
                candidates.add(Candidate(shapes[part] ?: return null, attributes))
            }
        }
        if (candidates.isEmpty()) {
            return null
        }

        return if (config.schemaMode == schemaStrict) {
            strict(candidates)
        }
        else {
            superset(candidates)
        }
    }


    private fun strict(candidates: List<Candidate>): HeaderListing? {
        var baseline: HeaderListing? = null
        for (candidate in candidates) {
            val header = effectiveHeader(candidate) ?: return null
            if (baseline != null && baseline != header) {
                return null
            }
            baseline = header
        }
        return baseline
    }


    private fun superset(candidates: List<Candidate>): HeaderListing? {
        val tabular = candidates.map { it.shape as? DataShape.Tabular ?: return null }
        val attributeNames = linkedSetOf<String>()
        candidates.forEach { attributeNames.addAll(it.attributes?.keys.orEmpty()) }
        val dataLabels = linkedSetOf<HeaderLabel>()
        tabular.forEach { dataLabels.addAll(it.header.values) }
        val dataNames = dataLabels.mapTo(linkedSetOf()) { it.text }
        if (attributeNames.any { it in dataNames }) {
            return null
        }
        return HeaderListing.ofUnique(attributeNames.toList())
            .append(HeaderListing(dataLabels.toList()))
    }


    private fun effectiveHeader(candidate: Candidate): HeaderListing? {
        val tabular = candidate.shape as? DataShape.Tabular ?: return null
        val attributes = candidate.attributes ?: return tabular.header
        val dataNames = tabular.header.values.mapTo(linkedSetOf()) { it.text }
        if (attributes.keys.any { it in dataNames }) {
            return null
        }
        return HeaderListing.ofUnique(attributes.keys.toList()).append(tabular.header)
    }


    private data class Candidate(
        val shape: DataShape,
        val attributes: Map<String, String>?
    )
}
