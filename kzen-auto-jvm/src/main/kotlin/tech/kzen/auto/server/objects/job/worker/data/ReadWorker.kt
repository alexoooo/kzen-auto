package tech.kzen.auto.server.objects.job.worker.data

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.api.DataSource
import tech.kzen.auto.common.data.model.DataManifest
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.auto.server.objects.job.worker.SourceWorker
import tech.kzen.auto.server.objects.job.worker.JobLaneDescriptor
import tech.kzen.auto.server.objects.job.worker.JobLaneAttempt
import tech.kzen.auto.server.objects.job.worker.JobLaneContext
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionContext
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionResolution
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.platform.ClassName


/**
 * Source-generic reader for resolved data manifests. Item mode opens each selected part in manifest order;
 * unit mode emits each [DataUnit] whole. A manifest and positional cursor are migration state, so directory
 * changes cannot alter a resumed run. Cursor pulls always use the resumed Worker's [JobControl].
 *
 * Item mode fixes one effective shape across every part and unit. Default `schemaMode=superset` inspects the
 * selected manifest first and projects compatible tabular parts to one ordered union; `schemaMode=strict`
 * requires every effective shape to match exactly. Mixed tabular/payload or incompatible payload shapes fail.
 * `attributes=columns` prepends ordered unit attributes to tabular records and rejects name collisions.
 * A fresh resolution appends one immutable trace event containing the full manifest digest/count and a bounded
 * first-units teaser; a migrated carried manifest is not resolved or logged again.
 * For a stream of already-resolved [DataUnit] payloads, use [ReadPartWorker]. Fan-out to several independent
 * readers still requires duplicate FormulaSource/manual channel wiring until J6 adds first-class fan-out.
 */
@Reflect
open class ReadWorker(
    output: ChannelOutput<DataValue>,
    private val source: ObjectReference?,
    private val emit: String,
    private val role: String,
    private val attributes: String,
    private val selfLocation: ObjectLocation,
    @Service private val openerLookup: DataOpenerLookup,
    private val schemaMode: String = DataReadCore.schemaSuperset
): SourceWorker(output, selfLocation) {
    companion object {
        const val emitItems = "items"
        const val emitUnits = "units"
        const val attributesIgnore = "ignore"
        const val attributesColumns = "columns"

        private val dataUnitType = TypeMetadata(
            ClassName(DataUnit::class.qualifiedName!!), emptyList(), false)
        private val logger = LoggerFactory.getLogger(ReadWorker::class.java)
    }


    private var sourceResolution: WorkerDefinitionResolution =
        WorkerDefinitionResolution.Failed("Worker definition context is not loaded")
    private var compatibilityKey: Digest? = null

    private var manifest: DataManifest? = null
    private var finished = false
    private var emitted = 0L
    private var unitIndex = 0
    private var partIndex = 0
    private var itemIndex = 0L
    private var shapeBaseline: DataReadCore.ShapeBaseline? = null
    private var inspectedShapes: Map<String, DataShape>? = null
    private var cursor: DataCursor? = null


    final override fun loadDefinitionContext(context: WorkerDefinitionContext) {
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
                addUtf8(attributes)
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
            if (schemaMode == DataReadCore.schemaSuperset && shapeBaseline == null) {
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
                    attributeValues(unit),
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
            emitter.send(JobDataValues.lift(unit))
        }
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
                val attributeValues = attributeValues(unit)
                val candidate = DataReadCore.effectiveShape(
                    activeCursor.shape,
                    attributeValues,
                    origin)
                if (inspectedShapes == null) {
                    shapeBaseline = DataReadCore.establishShape(shapeBaseline, candidate)
                }
            }

            val emittedItem = DataReadCore.emitNext(
                control,
                activeCursor,
                requireNotNull(shapeBaseline),
                attributeValues(unit),
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


    private fun attributeValues(unit: DataUnit): Map<String, String>? {
        return if (attributes == attributesColumns) unit.attributes else null
    }


    override suspend fun onClose() {
        DataReadCore.closeFallback(cursor)
        cursor = null
    }


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
        if (emit == emitUnits) {
            return JobLaneAttempt(
                JobLaneDescriptor(dataUnitType, HeaderListing.empty), null)
        }

        val staticShape = dataSource.staticShape(role.takeIf { it.isNotBlank() }?.let(::DataRole))
        if (attributes == attributesColumns) {
            return if (staticShape != null && LegacyDataShapeBridge.headerOrNull(staticShape) == null) {
                JobLaneAttempt(
                    JobLaneDescriptor.unknown,
                    "attributes=columns requires record data, found ${staticShape.itemType.structural}")
            }
            else {
                JobLaneAttempt(JobLaneDescriptor.unknown, null)
            }
        }

        if (staticShape == null) {
            return JobLaneAttempt(JobLaneDescriptor.unknown, null)
        }
        return JobLaneAttempt(JobLaneDescriptor(staticShape.itemType), null)
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
        if (emit == emitItems && attributes != attributesIgnore && attributes != attributesColumns) {
            return "Unknown Read attributes mode: $attributes"
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
