package tech.kzen.auto.client.objects.document.job.edit

import tech.kzen.auto.client.objects.document.job.source.DataSourceResolveStore
import tech.kzen.auto.client.objects.document.job.source.DataSourceShapeStore
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.auto.common.data.model.DataManifest
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.objects.document.job.JobChannelDerivation
import tech.kzen.auto.common.objects.document.job.JobServeCapability
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
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


    internal data class ReadProjectionConfig(
        val source: ObjectLocation,
        val emit: String,
        val role: String,
        val attributes: String,
        val schemaMode: String
    )


    internal fun choose(
        liveSummary: DataContract?,
        inspectedSource: ContractResult?
    ): Result? = when {
        liveSummary != null -> Result(Provider.LiveSummary, ContractResult.Available(liveSummary))
        inspectedSource != null && inspectedSource != ContractResult.Unavailable ->
            Result(Provider.InspectedSource, inspectedSource)
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
                        LegacyDataShapeBridge.tabular(
                            HeaderListing(summary.columnSummaries.map.keys.toList())).itemType,
                        null)
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
                        val projection = ReadShapeProjection.project(
                            manifest,
                            inspected.parts.mapValues { (_, state) ->
                                state.shape.takeIf { !state.inspecting && state.error == null }
                            },
                            config)
                        if (projection != ContractResult.Unavailable) {
                            return choose(null, projection)
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
    ): ContractResult {
        val config = readProjectionConfig(graphStructure, provider) ?: return ContractResult.Unavailable
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
    ): JobUpstreamSchema.ContractResult {
        if (config.emit != emitItems ||
            (config.attributes != attributesIgnore && config.attributes != attributesColumns) ||
            (config.schemaMode != schemaStrict && config.schemaMode != schemaSuperset)) {
            return JobUpstreamSchema.ContractResult.Unavailable
        }

        val candidates = mutableListOf<Candidate>()
        for (unit in manifest.units) {
            val selected = if (config.role.isNotBlank()) {
                unit.partsOf(DataRole(config.role)).takeIf { it.isNotEmpty() }
            }
            else {
                val roles = unit.parts.map { it.role }.distinct()
                roles.singleOrNull()?.let(unit::partsOf)
            } ?: return JobUpstreamSchema.ContractResult.Unavailable

            val attributes = if (config.attributes == attributesColumns) unit.attributes else null
            for (part in selected) {
                candidates.add(Candidate(
                    shapes[part] ?: return JobUpstreamSchema.ContractResult.Unavailable,
                    attributes))
            }
        }
        if (candidates.isEmpty()) {
            return JobUpstreamSchema.ContractResult.Unavailable
        }

        return if (config.schemaMode == schemaStrict) {
            strict(candidates)
        }
        else {
            superset(candidates)
        }
    }


    private fun strict(candidates: List<Candidate>): JobUpstreamSchema.ContractResult {
        var baseline: DataContract? = null
        for (candidate in candidates) {
            val effective = effectiveContract(candidate)
            if (effective !is JobUpstreamSchema.ContractResult.Available) {
                return effective
            }
            val contract = effective.contract
            if (baseline != null && baseline != contract) {
                return conflict("Strict read requires equal contracts", baseline, contract)
            }
            baseline = contract
        }
        return baseline
            ?.let(JobUpstreamSchema.ContractResult::Available)
            ?: JobUpstreamSchema.ContractResult.Unavailable
    }


    private fun superset(candidates: List<Candidate>): JobUpstreamSchema.ContractResult {
        val contracts = mutableListOf<DataContract>()
        val attributeNames = linkedSetOf<String>()
        for (candidate in candidates) {
            val contract = candidate.shape.itemType
            val record = contract.structural as? DataType.Record
                ?: return JobUpstreamSchema.ContractResult.Unavailable
            attributeNames.addAll(candidate.attributes?.keys.orEmpty())
            val collision = candidate.attributes?.keys?.firstOrNull { attribute ->
                candidates.any { other ->
                    (other.shape.itemType.structural as? DataType.Record)
                        ?.fields
                        ?.any { it.id.name == attribute } == true
                }
            }
            if (collision != null) {
                return JobUpstreamSchema.ContractResult.Error(
                    "Read attribute '$collision' conflicts with a data field in $contract")
            }
            contracts.add(contract)
        }
        if (contracts.isEmpty()) {
            return JobUpstreamSchema.ContractResult.Unavailable
        }

        val records = contracts.map { it.structural as DataType.Record }
        val nameOrder = linkedSetOf<String>()
        val exemplarById = linkedMapOf<FieldId, Pair<DataField, DataContract>>()
        val presenceById = mutableMapOf<FieldId, Int>()
        val optionalById = mutableMapOf<FieldId, Boolean>()
        for (contract in contracts) {
            val record = contract.structural as DataType.Record
            for (field in record.fields) {
                nameOrder.add(field.id.name)
                presenceById[field.id] = (presenceById[field.id] ?: 0) + 1
                optionalById[field.id] = optionalById[field.id] == true || field.optional
                val child = contract.child(DataPathSegment.Field(field.id))
                val previous = exemplarById[field.id]
                if (previous == null) {
                    exemplarById[field.id] = field to child
                }
                else if (previous.second != child) {
                    return conflict(
                        "Superset read has conflicting field '${field.id.name}#${field.id.occurrence}'",
                        previous.second,
                        child)
                }
            }
        }

        val dataFields = nameOrder.flatMap { name ->
            exemplarById.entries
                .filter { it.key.name == name }
                .sortedBy { it.key.occurrence }
                .map { (id, exemplar) ->
                    exemplar.first.copy(
                        optional = optionalById[id] == true || presenceById[id] != contracts.size)
                }
        }
        val attributeFields = attributeNames.map { name ->
            DataField(
                FieldId(name),
                DataType.Scalar(ScalarKind.Text),
                optional = candidates.any { name !in it.attributes.orEmpty() })
        }
        val nativeByPath = linkedMapOf<DataTypePath, tech.kzen.lib.common.model.structure.metadata.TypeMetadata>()
        contracts.forEach { contract ->
            contract.nativeByPath.forEach { (path, metadata) ->
                if (path != DataTypePath.root) {
                    nativeByPath[path] = metadata
                }
            }
        }
        return JobUpstreamSchema.ContractResult.Available(DataContract(
            DataType.Record(attributeFields + dataFields, records.any { it.nullable }),
            nativeByPath))
    }


    private fun effectiveContract(candidate: Candidate): JobUpstreamSchema.ContractResult {
        val contract = candidate.shape.itemType
        val record = contract.structural as? DataType.Record
            ?: return JobUpstreamSchema.ContractResult.Unavailable
        val attributes = candidate.attributes
            ?: return JobUpstreamSchema.ContractResult.Available(contract)
        val dataNames = record.fields.mapTo(linkedSetOf()) { it.id.name }
        val collision = attributes.keys.firstOrNull { it in dataNames }
        if (collision != null) {
            return JobUpstreamSchema.ContractResult.Error(
                "Read attribute '$collision' conflicts with a data field in $contract")
        }
        val attributeFields = attributes.keys.map { name ->
            DataField(FieldId(name), DataType.Scalar(ScalarKind.Text))
        }
        return JobUpstreamSchema.ContractResult.Available(DataContract(
            DataType.Record(attributeFields + record.fields, record.nullable),
            contract.nativeByPath))
    }


    private fun conflict(
        reason: String,
        first: DataContract,
        second: DataContract
    ): JobUpstreamSchema.ContractResult.Error =
        JobUpstreamSchema.ContractResult.Error("$reason: first=$first; second=$second")


    private data class Candidate(
        val shape: DataShape,
        val attributes: Map<String, String>?
    )
}
