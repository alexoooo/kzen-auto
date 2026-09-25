package tech.kzen.auto.server.objects.job.worker.data

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.data.design.DesignReadSession
import tech.kzen.auto.server.data.format.SourceFormatResolutionBudgetFactory
import tech.kzen.auto.server.data.read.detection.FilenameDetection
import tech.kzen.auto.server.objects.datasource.format.ConfiguredRecordFormatLookup
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.ExpandingTransformWorker
import tech.kzen.auto.server.objects.job.worker.JobLaneAttempt
import tech.kzen.auto.server.objects.job.worker.JobLaneContext
import tech.kzen.auto.server.objects.job.worker.JobLaneDescriptor
import tech.kzen.auto.server.objects.job.worker.content.Content
import tech.kzen.auto.server.objects.job.worker.content.FileValues
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionContext
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.platform.ClassName


/**
 * Reads what each incoming value holds and emits its items (docs/plans/2026-09-24_values-metadata-and-design-time-
 * types.md, R1): the single Worker that interprets content, whatever produced it. Each item's metadata is
 * `{parent}`, the incoming value's metadata — so a row knows its file (`parent.name`), or its archive member and
 * through it the archive (`parent.parent.name`) — and never the incoming payload, which is released once read.
 *
 * - **A file** (a [Content] with a [Content.reference], from `File`): [format] resolves against the file itself,
 *   automatic detection sampling it like any selected file, then the file is read as one part through the
 *   ordinary reader chain, so a resumed run adopts the open cursor or skips to its position.
 * - **Content read once** (an archive member from `Extract`, a lent element — borrowed elements, docs/plans/
 *   2026-09-16_borrowed-elements.md §3.8): it cannot be sampled ahead of reading, so automatic [format] picks by
 *   the member's name among the configured formats ([FilenameDetection.formatFor]) and fails by name when the
 *   name admits none; the bytes enter the reader chain below the content provider
 *   ([tech.kzen.auto.server.data.ContentDataOpener.openContent]). The first member fixes the item shape and
 *   every later one must match it (the `strict` rule of [DataReadCore.establishShape]). The cadence
 *   checkpoints inside a member do not park while it is lent ([tech.kzen.auto.server.objects.job.worker.
 *   BorrowingSource]); its cursor is closed at the end of the member, without draining when the downstream
 *   completes early, and never carried across a live edit.
 * - **A [DataUnit]**: its parts of [role] are read with their own resolved read specs ([format] does not apply),
 *   and the unit's attributes join the parent metadata as text fields.
 *
 * Several files or units share one shape: `schemaMode=superset` projects compatible tabular parts of a unit to
 * one ordered union, `strict` requires an exact match.
 *
 * Before Run the Worker types itself from the values its input lane offers (docs/plans/2026-09-24_values-metadata-
 * and-design-time-types.md, R5), merging their shapes across every value by the same [schemaMode]; a run then holds
 * every part to that validated type ([DataReadCore.fitShape], R6), so a part the data changed under fails by name
 * instead of changing the type the Workers below were compiled against.
 */
@Reflect
class ParseWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    private val role: String,
    private val format: ConfiguredRecordFormat,
    selfLocation: ObjectLocation,
    @Service private val openerLookup: DataOpenerLookup,
    @Service private val formatLookup: ConfiguredRecordFormatLookup,
    private val schemaMode: String = DataReadCore.schemaSuperset,
    @Service private val resolutionBudgetFactory: SourceFormatResolutionBudgetFactory =
        SourceFormatResolutionBudgetFactory()
): ExpandingTransformWorker(input, output, selfLocation) {
    companion object {
        private val dataUnitClassName = ClassName(DataUnit::class.qualifiedName!!)
        private val contentClassName = ClassName(Content::class.qualifiedName!!)

        private const val entryFingerprintIdentity = "tech.kzen.auto/archive-entry-v1"

        private val text = DataContract(DataType.Scalar(ScalarKind.Text))
    }


    // Lazy: only content read once, under an automatic format, needs the registered formats instantiated
    private var configuredFormats: Lazy<List<ConfiguredRecordFormat>> =
        lazy { error("Worker definition context is not loaded") }

    private var currentUnit: DataUnit? = null
    private var currentRef: DataRef? = null
    private var partIndex = 0
    private var itemIndex = 0L
    private var shapeBaseline: DataReadCore.ShapeBaseline? = null

    // The type this Worker was validated with, read from data before Run: every part must fit it (R6)
    private var validatedShape: DataReadCore.ShapeBaseline? = null
    private var completedUnits = 0L
    private var totalEmitted = 0L
    private var unitEmittedOrdinal = 0L
    private var skipRemaining = 0L
    private var inspectedShapes: Map<Int, DataShape>? = null
    private var unitShapePlan: DataReadCore.ShapeBaseline? = null
    private var cursor: DataCursor? = null

    // The metadata of every item of the incoming value; rebuilt from the value on replay, so not migrated
    private var itemMetadata: ValueMetadata? = null

    // The open reader over content read once; closed at the end of the content, never carried
    private var entryCursor: DataCursor? = null

    /** The content being read once, for the by-name refusal of a capture taken inside it. */
    private var entryName: String? = null


    override fun loadDefinitionContext(context: WorkerDefinitionContext) {
        configuredFormats = lazy { context.configuredFormats() }
    }


    override suspend fun onStart(control: JobControl) {
        configError()?.let { throw IllegalArgumentException(it) }
        validatedShape = DataReadCore.validatedShape(control.outputContract())
    }


    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        val incoming = JobDataValues.native(element)
        itemMetadata = itemMetadata(element.metadata, incoming as? DataUnit)
        try {
            when (incoming) {
                is DataUnit ->
                    readUnit(incoming, null, emit, control)

                is Content -> {
                    val ref = incoming.reference()
                    if (ref == null) {
                        readOnce(incoming, element.metadata?.value, emit, control)
                    }
                    else {
                        readUnit(fileUnit(ref, control), ref, emit, control)
                    }
                }

                else -> throw IllegalStateException(
                    "Parse requires content or a DataUnit, but received " +
                        (incoming?.let { "${it::class.qualifiedName}: $it" } ?: "null"))
            }
        }
        finally {
            itemMetadata = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** `{parent}`: the incoming value's metadata, with a unit's attributes as text fields. */
    private fun itemMetadata(incoming: ValueMetadata?, unit: DataUnit?): ValueMetadata? {
        val attributes = unit?.attributes.orEmpty()
        val parent =
            if (attributes.isEmpty()) {
                incoming?.value
            }
            else {
                DataOverlay.record(incoming?.value, attributes.map { (name, value) ->
                    FieldId(name) to LiteralDataValues.lift(value, text)
                })
            }
            ?: return null
        return ValueMetadata.of(DataOverlay.record(null, listOf(FieldId(FileValues.parent) to parent)))
    }


    /** A file as a one-part unit, its read resolved by [format] against the file (sampled when automatic). */
    private suspend fun fileUnit(ref: DataRef, control: JobControl): DataUnit {
        val active = currentUnit
        if (active != null && currentRef == ref) {
            // Migration replay of the file being read: its resolution is carried
            return active
        }
        return DataUnit(emptyMap(), listOf(filePart(WorkerDataContext(control), ref, null)))
    }


    /** [ref] as a part read by [format]; before Run, [design] resolves each file's read once per content. */
    private suspend fun filePart(context: DataContext, ref: DataRef, design: DesignReadSession?): DataPart {
        val fingerprint = DataContentFingerprint.localOrNull(ref)
        val read =
            if (design == null) {
                resolveRead(context, ref, fingerprint)
            }
            else {
                design.resolvedRead(format, ref, fingerprint) { resolveRead(context, ref, fingerprint) }
            }
        return DataPart(DataRole.main, ref, fingerprint, read)
    }


    private suspend fun resolveRead(
        context: DataContext,
        ref: DataRef,
        fingerprint: DataContentFingerprint?
    ): ResolvedReadSpec {
        val resolution = resolutionBudgetFactory.create().withinDeadline {
            val request = FormatResolutionRequest(
                context,
                ref,
                fingerprint,
                FilenameDetection.hints(ref.id.substringAfterLast('/').substringAfterLast('\\')),
                null,
                this)
            formatLookup.preflight(format)?.resolve(request)
                ?: format.resolve(request)
        }
        return resolution.resolvedRead
    }


    private suspend fun readUnit(unit: DataUnit, ref: DataRef?, emit: Emitter, control: JobControl) {
        val activeUnit = currentUnit
        if (activeUnit == null) {
            currentUnit = unit
            currentRef = ref
        }
        else {
            check(activeUnit == unit) {
                "Parse migration replay received a different value for unit $completedUnits"
            }
        }

        if (schemaMode == DataReadCore.schemaSuperset && unitShapePlan == null && validatedShape == null) {
            prepareSuperset(unit, control)
        }

        readCurrentUnit(unit, emit, control)
        currentUnit = null
        currentRef = null
        partIndex = 0
        itemIndex = 0
        unitEmittedOrdinal = 0
        skipRemaining = 0
        inspectedShapes = null
        unitShapePlan = null
        completedUnits += 1
    }


    private suspend fun prepareSuperset(unit: DataUnit, control: JobControl) {
        val parts = DataReadCore.parts(unit, role, completedUnits)
        val context = WorkerDataContext(control)
        val inspected = linkedMapOf<Int, DataShape>()
        val candidates = mutableListOf<DataReadCore.ShapeCandidate>()
        for ((index, part) in parts.withIndex()) {
            val opener = openerLookup.openerFor(part.ref)
            val shape = opener.inspectShape(context, part)
                ?: throw IllegalStateException(
                    "Unable to inspect data shape at unit $completedUnits part $index (${part.ref.display()})")
            val origin = "unit $completedUnits part $index (${part.ref.display()})"
            inspected[index] = shape
            candidates.add(DataReadCore.ShapeCandidate(shape, null, origin))
        }
        inspectedShapes = inspected
        if (candidates.isNotEmpty()) {
            unitShapePlan = DataReadCore.planShape(candidates, schemaMode)
            shapeBaseline = DataReadCore.establishShape(shapeBaseline, requireNotNull(unitShapePlan))
        }
    }


    private suspend fun readCurrentUnit(unit: DataUnit, emitter: Emitter, control: JobControl) {
        val parts = DataReadCore.parts(unit, role, completedUnits)
        val context = WorkerDataContext(control)
        while (partIndex < parts.size) {
            val part = parts[partIndex]
            var activeCursor = cursor
            if (activeCursor == null) {
                activeCursor = DataReadCore.open(context, openerLookup, part)
                cursor = activeCursor

                val inspected = inspectedShapes?.get(partIndex)
                if (inspected != null) {
                    check(activeCursor.shape.itemType == inspected.itemType) {
                        "Data shape changed after inspection at unit $completedUnits part $partIndex " +
                            "(${part.ref.display()}): inspected $inspected, opened ${activeCursor.shape}"
                    }
                }

                val origin = "unit $completedUnits part $partIndex (${part.ref.display()})"
                val candidate = DataReadCore.effectiveShape(activeCursor.shape, null, origin)
                val validated = validatedShape
                if (validated != null) {
                    shapeBaseline = DataReadCore.fitShape(validated, candidate, schemaMode)
                }
                else if (inspectedShapes == null) {
                    shapeBaseline = DataReadCore.establishShape(shapeBaseline, candidate)
                }

                if (skipRemaining != 0L) {
                    val skipped = DataReadCore.skipAvailable(control, activeCursor, skipRemaining)
                    skipRemaining -= skipped
                    itemIndex = skipped
                    if (skipRemaining != 0L) {
                        DataReadCore.close(control, activeCursor)
                        cursor = null
                        partIndex += 1
                        itemIndex = 0
                        continue
                    }
                }
                else if (itemIndex != 0L) {
                    DataReadCore.skipItems(control, activeCursor, itemIndex)
                }
            }

            val emittedItem = DataReadCore.emitNext(
                control,
                activeCursor,
                requireNotNull(shapeBaseline),
                null,
                claimBeforeSend = {
                    itemIndex += 1
                    unitEmittedOrdinal += 1
                    totalEmitted += 1
                },
                send = { emitter.send(it.withMetadata(itemMetadata)) })
            if (!emittedItem) {
                DataReadCore.close(control, activeCursor)
                cursor = null
                partIndex += 1
                itemIndex = 0
            }
        }

        check(skipRemaining == 0L) {
            "Unable to resume Parse unit $completedUnits at emitted item $unitEmittedOrdinal; " +
                "the selected role exhausted with $skipRemaining items left to skip"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun readOnce(content: Content, metadata: DataValue?, emitter: Emitter, control: JobControl) {
        val name = metadataText(metadata, FileValues.name) ?: content.descriptor().name
        val parentName = metadataText(metadataRecord(metadata, FileValues.parent), FileValues.name)
        val display = if (parentName == null) name else "$parentName!$name"
        val part = oncePart(content, name, display)
        val bytes = control.runBlockingIo { content.open() }
        // openContent owns the bytes from here: it closes them itself when the reader fails to open
        val opened: DataCursor = openerLookup.contentOpener().openContent(part, bytes)
        entryCursor = opened
        entryName = display

        var completed = false
        try {
            val candidate = DataReadCore.effectiveShape(opened.shape, null, "'$display'")
            val validated = validatedShape
            shapeBaseline =
                if (validated != null) {
                    DataReadCore.fitShape(validated, candidate, schemaMode)
                }
                else {
                    DataReadCore.establishShape(shapeBaseline, candidate)
                }

            while (true) {
                val emitted = DataReadCore.emitNext(
                    control,
                    opened,
                    requireNotNull(shapeBaseline),
                    null,
                    claimBeforeSend = { totalEmitted += 1 },
                    send = { emitter.send(it.withMetadata(itemMetadata)) })
                if (!emitted) {
                    break
                }
            }
            completed = true
        }
        finally {
            entryCursor = null
            entryName = null
            if (completed) {
                DataReadCore.close(control, opened)
            }
            else {
                // Failure or an early downstream close: released without draining the rest of the content
                DataReadCore.closeFallback(opened)
            }
        }
        completedUnits += 1
    }


    private fun oncePart(content: Content, name: String, display: String): DataPart {
        val ref = DataRef(null, display)
        val chosen =
            if (format.selectionKind == FormatSelectionKind.Automatic) {
                FilenameDetection.formatFor(name, configuredFormats.value)
                    ?: throw IllegalStateException(
                        "Parse cannot tell the format of '$display' from its name; select a format")
            }
            else {
                format
            }
        val descriptor = content.descriptor()
        val fingerprint = DataContentFingerprint(
            entryFingerprintIdentity,
            MapExecutionValue(linkedMapOf(
                "archive" to TextExecutionValue(display.substringBeforeLast('!', "")),
                "entry" to TextExecutionValue(name),
                "size" to TextExecutionValue(descriptor.length?.toString() ?: ""),
                "modified" to TextExecutionValue(descriptor.modifiedEpochMillis?.toString() ?: ""))))
        // Content read once cannot be sampled, so there is no request to resolve against: the chosen format's
        // own read applies
        @Suppress("DEPRECATION")
        val read = chosen.resolvedRead(ref)
        return DataPart(DataRole.main, ref, fingerprint, read)
    }


    private fun metadataRecord(metadata: DataValue?, name: String): DataValue? {
        val record = metadata?.type as? DataType.Record
            ?: return null
        if (record.fields.none { it.id == FieldId(name) }) {
            return null
        }
        val node = metadata.access.field(metadata.root, FieldId(name))
        if (metadata.access.state(node) != DataState.Present) {
            return null
        }
        return DataValue(metadata.access, node)
    }


    private fun metadataText(metadata: DataValue?, name: String): String? {
        val field = metadataRecord(metadata, name)
            ?: return null
        if (field.type !is DataType.Scalar) {
            return null
        }
        return field.access.readText(field.root)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onExpansionClose() {
        DataReadCore.closeFallback(cursor)
        cursor = null
        DataReadCore.closeFallback(entryCursor)
        entryCursor = null
        entryName = null
    }


    override fun captureExpansionState(): Any {
        val detached = DataReadCore.detach(cursor)
        cursor = null
        return ParseState(
            role,
            currentUnit,
            currentRef,
            partIndex,
            itemIndex,
            shapeBaseline,
            completedUnits,
            totalEmitted,
            unitEmittedOrdinal,
            inspectedShapes,
            unitShapePlan,
            schemaMode,
            detached,
            entryName)
    }


    override fun loadExpansionState(captured: Any?) {
        val state = captured as? ParseState
        if (state == null) {
            (captured as? AutoCloseable)?.close()
            return
        }
        val interrupted = state.interruptedEntry
        if (interrupted != null) {
            // Content read once comes from a single-open stream (borrowed elements §3.6): the capture was taken
            // inside it, so the rebuilt instance cannot resume — refused by name, like the lender
            state.close()
            error("Parse was interrupted inside '$interrupted'; a live edit applies only between " +
                "elements. Start a new run to apply it.")
        }

        currentUnit = state.currentUnit
        currentRef = state.currentRef
        shapeBaseline = state.shapeBaseline
        completedUnits = state.completedUnits
        totalEmitted = state.totalEmitted
        unitEmittedOrdinal = state.unitEmittedOrdinal
        inspectedShapes = state.inspectedShapes
        unitShapePlan = state.unitShapePlan

        if (role == state.role && schemaMode == state.schemaMode) {
            partIndex = state.partIndex
            itemIndex = state.itemIndex
            val expectedIdentity = currentUnit
                ?.let { unit -> DataReadCore.parts(unit, role, completedUnits).getOrNull(partIndex) }
                ?.let(openerLookup::adoptionIdentity)
            cursor = state.adoptCursor(expectedIdentity)
            return
        }

        state.close()
        partIndex = 0
        itemIndex = 0
        skipRemaining = state.unitEmittedOrdinal
        inspectedShapes = null
        unitShapePlan = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The item's metadata is `{parent}` over the input's metadata (a unit's attributes are known only when read,
     * so its parent is dynamic); the payload is the declared shape of an explicit [format] over content, otherwise
     * the shape read from the input lane's sample before Run ([DesignShapeInference]), and otherwise known only
     * once read.
     */
    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        val configError = configError()
        if (configError != null) {
            return JobLaneAttempt(JobLaneDescriptor.unknown, configError)
        }

        if (input.contract.structural is DataType.Dynamic) {
            return JobLaneAttempt(JobLaneDescriptor.unknown, null)
        }

        val inputType = input.contract.nativeByPath[DataTypePath.root]
        val structural = input.contract.structural
        // A unit is identified by its native root type, whether its lane describes the record or leaves it opaque
        val unit = !structural.nullable &&
            inputType != null &&
            inputType.className == dataUnitClassName &&
            inputType.generics.isEmpty() &&
            !inputType.nullable
        val content = inputType != null && inputType.className == contentClassName && !inputType.nullable
        if (!unit && !content) {
            val description = inputType?.toSimple() ?: input.contract.structural.toString()
            return JobLaneAttempt(
                JobLaneDescriptor.unknown,
                "Parse requires content or a DataUnit, found $description")
        }

        val parent =
            if (unit) {
                DataContract(DataType.Dynamic())
            }
            else {
                input.contract.metadata?.contract
            }
        val metadata = parent?.let {
            MetadataContract.of(DataOverlay.contract(null, listOf(FieldId(FileValues.parent) to it)))
        }
        val declared = format
            .takeIf { content && it.selectionKind != FormatSelectionKind.Automatic }
            ?.declaredShape()
            ?.itemType
        if (declared != null) {
            return JobLaneAttempt(JobLaneDescriptor(declared.withMetadata(metadata)), null)
        }

        val unknown = JobLaneDescriptor.unknown.contract.withMetadata(metadata)
        val design = context.design
        val sample = input.sample
        if (design == null || sample == null) {
            return JobLaneAttempt(JobLaneDescriptor(unknown), null)
        }
        return DesignShapeInference
            .infer(sample.values.size, sample.total, design, openerLookup, schemaMode) { dataContext, index ->
                partsOf(dataContext, sample.values[index], index, design)
            }
            .attempt(unknown)
    }


    /** The parts a value holds, as a run reads them; null for content read once, which cannot be looked at ahead. */
    private suspend fun partsOf(
        context: DataContext,
        value: DataValue,
        index: Int,
        design: DesignReadSession
    ): List<DataPart>? =
        when (val incoming = JobDataValues.native(value)) {
            is DataUnit -> DataReadCore.parts(incoming, role, index)
            is Content -> incoming.reference()?.let { listOf(filePart(context, it, design)) }
            else -> null
        }


    override fun progress(snapshot: Any?): Map<String, Any?> {
        return mapOf(
            "units" to completedUnits,
            "emitted" to totalEmitted)
    }


    private fun configError(): String? {
        if (schemaMode != DataReadCore.schemaStrict && schemaMode != DataReadCore.schemaSuperset) {
            return "Unknown Parse schema mode: $schemaMode"
        }
        return null
    }


    private class ParseState(
        val role: String,
        val currentUnit: DataUnit?,
        val currentRef: DataRef?,
        val partIndex: Int,
        val itemIndex: Long,
        val shapeBaseline: DataReadCore.ShapeBaseline?,
        val completedUnits: Long,
        val totalEmitted: Long,
        val unitEmittedOrdinal: Long,
        val inspectedShapes: Map<Int, DataShape>?,
        val unitShapePlan: DataReadCore.ShapeBaseline?,
        val schemaMode: String,
        private var detachedCursor: DataReadCore.DetachedCursor?,
        val interruptedEntry: String?
    ): AutoCloseable {
        fun adoptCursor(
            expectedIdentity: tech.kzen.auto.common.data.read.CursorAdoptionIdentity?
        ): DataCursor? {
            val detached = detachedCursor
            detachedCursor = null
            return DataReadCore.adopt(detached, expectedIdentity)
        }


        override fun close() {
            val closing = detachedCursor
            detachedCursor = null
            closing?.close()
        }
    }
}
