package tech.kzen.auto.server.objects.job.worker.data

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.api.DataSource
import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.model.DataManifest
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.data.design.DesignReadSession
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.auto.server.objects.job.worker.SourceWorker
import tech.kzen.auto.server.objects.job.worker.JobLaneDescriptor
import tech.kzen.auto.server.objects.job.worker.JobLaneSample
import tech.kzen.auto.server.objects.job.worker.JobLaneAttempt
import tech.kzen.auto.server.objects.job.worker.JobLaneContext
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionContext
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionResolution
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.util.digest.Digest
import kotlin.reflect.typeOf


/**
 * Source-generic reader for resolved data manifests. With `emit=items` it opens each selected part in manifest order
 * and emits its items; with `emit=units` it emits each [DataUnit] whole, its attributes as the value's metadata (the
 * payload for a Run per unit, or a `Parse` below). The mode is the Worker's own setting, never its neighbour's.
 * A manifest and positional cursor are migration state, so directory changes cannot alter a resumed run. Cursor
 * pulls always use the resumed Worker's [JobControl].
 *
 * Item mode fixes one effective shape across every part and unit. Default `schemaMode=superset` inspects the
 * selected manifest first and projects compatible tabular parts to one ordered union; `schemaMode=strict`
 * requires every effective shape to match exactly. Mixed tabular/payload or incompatible payload shapes fail.
 * A fresh resolution appends one immutable trace event containing the full manifest digest/count and a bounded
 * first-units teaser; a migrated carried manifest is not resolved or logged again.
 * Before Run (docs/plans/2026-09-24_values-metadata-and-design-time-types.md, R5), item mode reads its item type from
 * the source's data when the source declares none ([DesignShapeInference]), and unit mode offers its first units as
 * the lane's sample; the resolved manifest is the validation's evidence. A run holds every part to the validated
 * type ([DataReadCore.fitShape], R6).
 * To read a stream of file values or data units, use `Parse` ([ParseWorker]). Fan-out to several independent
 * readers still requires duplicate FormulaSource/manual channel wiring until J6 adds first-class fan-out.
 */
@Reflect
open class ReadWorker(
    output: ChannelOutput<DataValue>,
    private val source: ObjectReference?,
    private val role: String,
    private val selfLocation: ObjectLocation,
    @Service private val openerLookup: DataOpenerLookup,
    private val schemaMode: String = DataReadCore.schemaSuperset,
    private val emit: String = emitItems
): SourceWorker(output, selfLocation) {
    companion object {
        const val emitItems = "items"
        const val emitUnits = "units"

        // The described record, exactly as each emitted unit lifts, so downstream ordinal accessors line up
        private val dataUnitContract by lazy { JobDataValues.describe(typeOf<DataUnit>()) }
        // A unit's attributes are known only once resolved (read them by name, `meta["date"]`)
        private val unitMetadataContract = MetadataContract.empty
        private val text = DataContract(DataType.Scalar(ScalarKind.Text))
        private val logger = LoggerFactory.getLogger(ReadWorker::class.java)
        private val sourceAttribute = AttributeName("source")
    }


    private var sourceResolution: WorkerDefinitionResolution =
        WorkerDefinitionResolution.Failed("Worker definition context is not loaded")
    // Lazy: only a source detecting its files by name needs the registered formats instantiated
    private var configuredFormats: Lazy<List<ConfiguredRecordFormat>> =
        lazy { error("Worker definition context is not loaded") }
    private var compatibilityKey: Digest? = null

    private var manifest: DataManifest? = null
    private var finished = false
    private var emitted = 0L
    private var unitIndex = 0
    private var partIndex = 0
    private var itemIndex = 0L
    private var shapeBaseline: DataReadCore.ShapeBaseline? = null
    // The type this Worker was validated with: every part must fit it (R6)
    private var validatedShape: DataReadCore.ShapeBaseline? = null
    private var inspectedShapes: Map<String, DataShape>? = null
    private var cursor: DataCursor? = null


    final override fun loadDefinitionContext(context: WorkerDefinitionContext) {
        configuredFormats = lazy { context.configuredFormats() }
        val resolved = resolveSource(context)
        val dataSource = (resolved as? WorkerDefinitionResolution.Resolved)?.value as? DataSource
        val dependencyDigests = try {
            dataSource
                ?.definitionDependencies()
                ?.sortedBy { it.asString() }
                ?.map(context::definitionDependencyDigest)
                ?: emptyList()
        }
        catch (e: IllegalArgumentException) {
            loadSourceResolution(WorkerDefinitionResolution.Failed(
                "Unable to prepare data source definition dependency: ${e.message}"))
            return
        }
        loadSourceResolution(resolved, dependencyDigests)
    }


    protected open fun resolveSource(context: WorkerDefinitionContext): WorkerDefinitionResolution {
        val reference = source
        if (reference == null) {
            return WorkerDefinitionResolution.Failed("No data source selected")
        }

        return context.resolve(reference, selfLocation)
    }


    internal fun loadSourceResolution(
        resolution: WorkerDefinitionResolution,
        dependencyDigests: List<Digest> = emptyList()
    ) {
        sourceResolution =
            if (resolution is WorkerDefinitionResolution.Resolved && resolution.value !is DataSource) {
                WorkerDefinitionResolution.Failed(
                    "Referenced object is not a DataSource: ${resolution.location} " +
                        "(${resolution.value::class.qualifiedName})")
            }
            else {
                resolution
            }

        compatibilityKey = (sourceResolution as? WorkerDefinitionResolution.Resolved)?.let {
            Digest.build {
                addDigestible(it.location)
                addDigest(it.cacheKey)
                dependencyDigests.forEach(::addDigest)
                addUtf8(emit)
                addUtf8(role)
                addUtf8(schemaMode)
            }
        }
    }


    override suspend fun produce(emit: Emitter, control: JobControl) {
        validateConfig()
        if (finished) {
            return
        }

        val context = WorkerDataContext(control)
        val activeManifest = manifest ?: resolveManifest(context, control).also { manifest = it }
        if (this.emit == emitUnits) {
            emitUnits(activeManifest, emit)
        }
        else {
            validatedShape = DataReadCore.validatedShape(control.outputContract())
            if (schemaMode == DataReadCore.schemaSuperset && shapeBaseline == null && validatedShape == null) {
                prepareSuperset(activeManifest, context)
            }
            emitItems(activeManifest, context, emit, control)
        }
        finished = true
    }


    private suspend fun prepareSuperset(activeManifest: DataManifest, context: WorkerDataContext) {
        val inspected = linkedMapOf<String, DataShape>()
        val candidates = mutableListOf<DataReadCore.ShapeCandidate>()
        for ((unitIndex, unit) in activeManifest.units.withIndex()) {
            val parts = DataReadCore.parts(unit, role, unitIndex)
            for ((partIndex, part) in parts.withIndex()) {
                val opener = openerLookup.openerFor(part.ref)
                val shape = opener.inspectShape(context, part)
                    ?: throw IllegalStateException(
                        "Unable to inspect data shape at unit $unitIndex part $partIndex (${part.ref.display()})")
                val origin = "unit $unitIndex part $partIndex (${part.ref.display()})"
                inspected[partKey(unitIndex, partIndex)] = shape
                candidates.add(DataReadCore.ShapeCandidate(
                    shape,
                    null,
                    origin))
            }
        }
        inspectedShapes = inspected
        if (candidates.isNotEmpty()) {
            shapeBaseline = DataReadCore.planShape(candidates, schemaMode)
        }
    }


    private suspend fun resolveManifest(context: WorkerDataContext, control: JobControl): DataManifest {
        val resolved = sourceResolution as? WorkerDefinitionResolution.Resolved
            ?: throw IllegalStateException((sourceResolution as WorkerDefinitionResolution.Failed).message)
        val dataSource = resolved.value as DataSource
        val result = dataSource.resolve(context)
        for (diagnostic in result.diagnostics) {
            logger.warn(
                "Data source {} diagnostic {}: {}",
                resolved.location, diagnostic.kind, diagnostic.message)
        }
        val resolvedManifest = result.manifest
        val teaserCount = JobConventions.progressTeaserRowCount
        control.log(
            selfLocation,
            linkedMapOf(
                "digest" to resolvedManifest.digest().toString(),
                "totalCount" to resolvedManifest.units.size.toLong(),
                "teasedManifest" to DataManifest(resolvedManifest.units.take(teaserCount)).asExecutionValue(),
                "truncated" to (resolvedManifest.units.size > teaserCount)))
        return resolvedManifest
    }


    private suspend fun emitUnits(activeManifest: DataManifest, emitter: Emitter) {
        while (unitIndex < activeManifest.units.size) {
            val unit = activeManifest.units[unitIndex]
            unitIndex += 1
            emitted += 1
            emitter.send(unitValue(unit))
        }
    }


    /** A unit sent whole: its attributes are the value's metadata. */
    private fun unitValue(unit: DataUnit): DataValue {
        val metadata = DataOverlay.record(null, unit.attributes.map { (name, value) ->
            FieldId(name) to LiteralDataValues.lift(value, text)
        })
        return JobDataValues.lift(unit).withMetadata(ValueMetadata.of(metadata))
    }


    private suspend fun emitItems(
        activeManifest: DataManifest,
        context: WorkerDataContext,
        emitter: Emitter,
        control: JobControl
    ) {
        while (unitIndex < activeManifest.units.size) {
            val unit = activeManifest.units[unitIndex]
            val parts = DataReadCore.parts(unit, role, unitIndex)
            if (partIndex >= parts.size) {
                unitIndex += 1
                partIndex = 0
                itemIndex = 0
                continue
            }

            val part = parts[partIndex]
            var activeCursor = cursor
            if (activeCursor == null) {
                activeCursor = DataReadCore.open(context, openerLookup, part)
                cursor = activeCursor
                val inspected = inspectedShapes?.get(partKey(unitIndex, partIndex))
                if (inspected != null) {
                    check(activeCursor.shape.itemType == inspected.itemType) {
                        "Data shape changed after inspection at unit $unitIndex part $partIndex " +
                            "(${part.ref.display()}): inspected $inspected, opened ${activeCursor.shape}"
                    }
                }
                if (itemIndex != 0L) {
                    DataReadCore.skipItems(control, activeCursor, itemIndex)
                }
                val origin = "unit $unitIndex part $partIndex (${part.ref.display()})"
                val candidate = DataReadCore.effectiveShape(
                    activeCursor.shape,
                    null,
                    origin)
                val validated = validatedShape
                if (validated != null) {
                    shapeBaseline = DataReadCore.fitShape(validated, candidate, schemaMode)
                }
                else if (inspectedShapes == null) {
                    shapeBaseline = DataReadCore.establishShape(shapeBaseline, candidate)
                }
            }

            val emittedItem = DataReadCore.emitNext(
                control,
                activeCursor,
                requireNotNull(shapeBaseline),
                null,
                claimBeforeSend = {
                    itemIndex += 1
                    emitted += 1
                },
                send = emitter::send)
            if (!emittedItem) {
                DataReadCore.close(control, activeCursor)
                cursor = null
                partIndex += 1
                itemIndex = 0
                continue
            }
        }
    }


    private fun partKey(unitIndex: Int, partIndex: Int): String = "$unitIndex:$partIndex"


    override suspend fun onClose() {
        DataReadCore.closeFallback(cursor)
        cursor = null
    }


    /**
     * The selected data source: a different one is refused while the run is open over the previous
     * ([tech.kzen.auto.server.exec.job.JobLogic.refuseMigration]); an edit inside the same data source is judged
     * by the definition digest on adoption ([loadMigrationState]), which restarts the read from a fresh manifest.
     */
    override fun migrationKey(graphNotation: GraphNotation, location: ObjectLocation): Any =
        migrationKeyOf(graphNotation, location, sourceAttribute)


    override fun captureMigrationState(): Any {
        val detached = DataReadCore.detach(cursor)
        cursor = null
        return ReadState(
            compatibilityKey, manifest, finished, emitted, unitIndex, partIndex, itemIndex,
            shapeBaseline, inspectedShapes, detached)
    }


    override fun loadMigrationState(captured: Any?) {
        val state = captured as? ReadState
        if (state == null) {
            (captured as? AutoCloseable)?.close()
            return
        }
        val currentKey = compatibilityKey
        if (currentKey == null || currentKey != state.compatibilityKey) {
            state.close()
            return
        }

        manifest = state.manifest
        finished = state.finished
        emitted = state.emitted
        unitIndex = state.unitIndex
        partIndex = state.partIndex
        itemIndex = state.itemIndex
        shapeBaseline = state.shapeBaseline
        inspectedShapes = state.inspectedShapes
        cursor = state.adoptCursor(currentAdoptionIdentity())
    }


    private fun currentAdoptionIdentity() = manifest
        ?.units
        ?.getOrNull(unitIndex)
        ?.let { unit -> DataReadCore.parts(unit, role, unitIndex).getOrNull(partIndex) }
        ?.let(openerLookup::adoptionIdentity)


    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        val configError = configError()
        if (configError != null) {
            return JobLaneAttempt(JobLaneDescriptor.unknown, configError)
        }

        val resolved = sourceResolution as? WorkerDefinitionResolution.Resolved
            ?: return JobLaneAttempt(
                JobLaneDescriptor.unknown,
                (sourceResolution as WorkerDefinitionResolution.Failed).message)
        val dataSource = resolved.value as DataSource
        val design = context.design
        if (emit == emitUnits) {
            val lane = dataUnitContract.withMetadata(unitMetadataContract)
            if (design == null) {
                return JobLaneAttempt(JobLaneDescriptor(lane), null)
            }
            return when (val read = designManifest(dataSource, design)) {
                is DesignManifest.Read -> JobLaneAttempt(
                    JobLaneDescriptor(lane, sample = JobLaneSample(
                        read.manifest.units.take(design.budget.maxValues).map(::unitValue),
                        read.manifest.units.size)),
                    null)
                is DesignManifest.Unread -> JobLaneAttempt(JobLaneDescriptor(lane), null, read.warning)
                DesignManifest.Limited -> JobLaneAttempt(JobLaneDescriptor(lane), null, partial = true)
            }
        }

        val staticShape = dataSource.staticShape(
            role.takeIf { it.isNotBlank() }?.let(::DataRole), configuredFormats)
        if (staticShape != null) {
            return JobLaneAttempt(JobLaneDescriptor(staticShape.itemType), null)
        }
        if (design == null) {
            return JobLaneAttempt(JobLaneDescriptor.unknown, null)
        }
        return when (val read = designManifest(dataSource, design)) {
            is DesignManifest.Read -> {
                val units = read.manifest.units
                DesignShapeInference
                    .infer(
                        minOf(units.size, design.budget.maxValues), units.size, design, openerLookup, schemaMode
                    ) { _, index ->
                        DataReadCore.parts(units[index], role, index)
                    }
                    .attempt(JobLaneDescriptor.unknown.contract)
            }
            is DesignManifest.Unread -> JobLaneAttempt(JobLaneDescriptor.unknown, null, read.warning)
            DesignManifest.Limited -> JobLaneAttempt(JobLaneDescriptor.unknown, null, partial = true)
        }
    }


    private sealed interface DesignManifest {
        class Read(val manifest: DataManifest): DesignManifest
        class Unread(val warning: String?): DesignManifest
        data object Limited: DesignManifest
    }


    /** The source's manifest before Run, recorded as evidence so a change to the data makes the validation stale. */
    private fun designManifest(dataSource: DataSource, design: DesignReadSession): DesignManifest =
        try {
            design
                .observe(
                    "the data of ${selfLocation.objectPath.name.value}",
                    { dataSource.resolve(it).manifest },
                    DataManifest::digest)
                ?.let { DesignManifest.Read(it.value) }
                ?: DesignManifest.Limited
        }
        catch (_: UnsupportedOperationException) {
            // A source that runs logic to resolve is known only in a run
            DesignManifest.Unread(null)
        }
        catch (e: Exception) {
            DesignManifest.Unread("Data not read before Run: ${e.message}")
        }


    override fun progress(snapshot: Any?): Map<String, Any?> {
        return mapOf(
            "units" to (manifest?.units?.size?.toLong() ?: 0L),
            "unit" to unitIndex.toLong(),
            "emitted" to emitted)
    }


    private fun validateConfig() {
        configError()?.let { throw IllegalArgumentException(it) }
    }


    private fun configError(): String? {
        if (emit != emitItems && emit != emitUnits) {
            return "Unknown Read emit mode: $emit"
        }
        if (schemaMode != DataReadCore.schemaStrict && schemaMode != DataReadCore.schemaSuperset) {
            return "Unknown Read schema mode: $schemaMode"
        }
        return null
    }


    private class ReadState(
        val compatibilityKey: Digest?,
        val manifest: DataManifest?,
        val finished: Boolean,
        val emitted: Long,
        val unitIndex: Int,
        val partIndex: Int,
        val itemIndex: Long,
        val shapeBaseline: DataReadCore.ShapeBaseline?,
        val inspectedShapes: Map<String, DataShape>?,
        private val detachedCursor: DataReadCore.DetachedCursor?
    ): AutoCloseable {
        fun adoptCursor(
            expectedIdentity: tech.kzen.auto.common.data.read.CursorAdoptionIdentity?
        ): DataCursor? {
            return DataReadCore.adopt(detachedCursor, expectedIdentity)
        }


        override fun close() {
            detachedCursor?.close()
        }
    }
}
