package tech.kzen.auto.server.objects.job.worker.data

import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.ExpandingTransformWorker
import tech.kzen.auto.server.objects.job.worker.JobMessage
import tech.kzen.auto.server.objects.job.worker.WorkerLane
import tech.kzen.auto.server.objects.job.worker.WorkerLaneAttempt
import tech.kzen.auto.server.objects.job.worker.WorkerLaneContext
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.platform.ClassName


/**
 * Expands each input [DataUnit] into the items of its selected parts. It complements [ReadWorker], whose source
 * resolves the units itself. The active unit, part/item positions, shape baseline, and open cursor all migrate
 * with [ExpandingTransformWorker]'s active physical input batch.
 *
 * Fan-out to several independently configured readers requires duplicate FormulaSource/manual channel wiring
 * until J6 adds first-class fan-out. A single ReadPart owns and consumes its incoming DataUnit stream.
 */
@Reflect
class ReadPartWorker(
    input: ChannelInput<Any?>,
    output: ChannelOutput<Any?>,
    private val role: String,
    private val attributes: String,
    selfLocation: ObjectLocation,
    @Service private val openerLookup: DataOpenerLookup,
    private val schemaMode: String = DataReadCore.schemaSuperset
): ExpandingTransformWorker(input, output, selfLocation) {
    companion object {
        const val attributesIgnore = ReadWorker.attributesIgnore
        const val attributesColumns = ReadWorker.attributesColumns

        private val dataUnitClassName = ClassName(DataUnit::class.qualifiedName!!)
    }


    private var currentUnit: DataUnit? = null
    private var partIndex = 0
    private var itemIndex = 0L
    private var shapeBaseline: DataReadCore.ShapeBaseline? = null
    private var completedUnits = 0L
    private var totalEmitted = 0L
    private var unitEmittedOrdinal = 0L
    private var skipRemaining = 0L
    private var inspectedShapes: Map<Int, DataShape>? = null
    private var unitShapePlan: DataReadCore.ShapeBaseline? = null
    private var cursor: DataCursor? = null


    override suspend fun onStart(control: JobControl) {
        configError()?.let { throw IllegalArgumentException(it) }
    }


    override suspend fun onElement(element: JobMessage, emit: Emitter, control: JobControl) {
        val incoming = element.payload as? DataUnit
            ?: throw IllegalStateException(
                "ReadPart requires a non-null DataUnit payload, but received " +
                    (element.payload?.let { "${it::class.qualifiedName}: $it" } ?: "null"))

        val activeUnit = currentUnit
        if (activeUnit == null) {
            currentUnit = incoming
        }
        else {
            check(activeUnit == incoming) {
                "ReadPart migration replay received a different DataUnit for unit $completedUnits"
            }
        }

        if (schemaMode == DataReadCore.schemaSuperset && unitShapePlan == null) {
            prepareSuperset(incoming, control)
        }

        readCurrentUnit(incoming, emit, control)
        currentUnit = null
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
            val shape = openerLookup.openerFor(part.ref).inspectShape(context, part)
                ?: throw IllegalStateException(
                    "Unable to inspect data shape at unit $completedUnits part $index (${part.ref.display()})")
            val origin = "unit $completedUnits part $index (${part.ref.display()})"
            inspected[index] = shape
            candidates.add(DataReadCore.ShapeCandidate(shape, attributeValues(unit), origin))
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
                    check(activeCursor.shape == inspected) {
                        "Data shape changed after inspection at unit $completedUnits part $partIndex " +
                            "(${part.ref.display()}): inspected $inspected, opened ${activeCursor.shape}"
                    }
                }

                val origin = "unit $completedUnits part $partIndex (${part.ref.display()})"
                val candidate = DataReadCore.effectiveShape(
                    activeCursor.shape, attributeValues(unit), origin)
                if (inspectedShapes == null) {
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
                attributeValues(unit),
                claimBeforeSend = {
                    itemIndex += 1
                    unitEmittedOrdinal += 1
                    totalEmitted += 1
                },
                send = emitter::send)
            if (!emittedItem) {
                DataReadCore.close(control, activeCursor)
                cursor = null
                partIndex += 1
                itemIndex = 0
            }
        }

        check(skipRemaining == 0L) {
            "Unable to resume ReadPart unit $completedUnits at emitted item $unitEmittedOrdinal; " +
                "the selected role exhausted with $skipRemaining items left to skip"
        }
    }


    private fun attributeValues(unit: DataUnit): Map<String, String>? {
        return if (attributes == attributesColumns) unit.attributes else null
    }


    override fun onExpansionClose() {
        DataReadCore.closeFallback(cursor)
        cursor = null
    }


    override fun captureExpansionState(): Any {
        val detached = DataReadCore.detach(cursor)
        cursor = null
        return ReadPartState(
            role,
            attributes,
            currentUnit,
            partIndex,
            itemIndex,
            shapeBaseline,
            completedUnits,
            totalEmitted,
            unitEmittedOrdinal,
            inspectedShapes,
            unitShapePlan,
            schemaMode,
            detached)
    }


    override fun loadExpansionState(captured: Any?) {
        val state = captured as? ReadPartState
        if (state == null) {
            (captured as? AutoCloseable)?.close()
            return
        }

        currentUnit = state.currentUnit
        shapeBaseline = state.shapeBaseline
        completedUnits = state.completedUnits
        totalEmitted = state.totalEmitted
        unitEmittedOrdinal = state.unitEmittedOrdinal
        inspectedShapes = state.inspectedShapes
        unitShapePlan = state.unitShapePlan

        if (role == state.role && attributes == state.attributes && schemaMode == state.schemaMode) {
            partIndex = state.partIndex
            itemIndex = state.itemIndex
            cursor = state.adoptCursor()
            return
        }

        state.close()
        partIndex = 0
        itemIndex = 0
        skipRemaining = state.unitEmittedOrdinal
        inspectedShapes = null
        unitShapePlan = null
    }


    override fun payloadFlow(input: WorkerLane, context: WorkerLaneContext): WorkerLaneAttempt {
        val configError = configError()
        if (configError != null) {
            return WorkerLaneAttempt(WorkerLane.unknown, configError)
        }

        if (input.payloadType == null && input.flatColumns == null) {
            return WorkerLaneAttempt(WorkerLane.unknown, null)
        }

        val inputType = input.payloadType
        val valid = inputType != null &&
            inputType.className == dataUnitClassName &&
            inputType.generics.isEmpty() &&
            !inputType.nullable
        if (!valid) {
            val description = inputType?.toSimple()
                ?: "flat columns ${input.flatColumns?.render() ?: "unknown"}"
            return WorkerLaneAttempt(
                WorkerLane.unknown,
                "ReadPart requires a non-null DataUnit payload, found $description")
        }

        return WorkerLaneAttempt(WorkerLane.unknown, null)
    }


    override fun progress(snapshot: Any?): Map<String, Any?> {
        return mapOf(
            "units" to completedUnits,
            "emitted" to totalEmitted)
    }


    private fun configError(): String? {
        if (attributes != attributesIgnore && attributes != attributesColumns) {
            return "Unknown ReadPart attributes mode: $attributes"
        }
        if (schemaMode != DataReadCore.schemaStrict && schemaMode != DataReadCore.schemaSuperset) {
            return "Unknown ReadPart schema mode: $schemaMode"
        }
        return null
    }


    private class ReadPartState(
        val role: String,
        val attributes: String,
        val currentUnit: DataUnit?,
        val partIndex: Int,
        val itemIndex: Long,
        val shapeBaseline: DataReadCore.ShapeBaseline?,
        val completedUnits: Long,
        val totalEmitted: Long,
        val unitEmittedOrdinal: Long,
        val inspectedShapes: Map<Int, DataShape>?,
        val unitShapePlan: DataReadCore.ShapeBaseline?,
        val schemaMode: String,
        private var detachedCursor: DataReadCore.DetachedCursor?
    ): AutoCloseable {
        fun adoptCursor(): DataCursor? {
            val detached = detachedCursor
            detachedCursor = null
            return DataReadCore.adopt(detached)
        }


        override fun close() {
            val closing = detachedCursor
            detachedCursor = null
            closing?.close()
        }
    }
}
