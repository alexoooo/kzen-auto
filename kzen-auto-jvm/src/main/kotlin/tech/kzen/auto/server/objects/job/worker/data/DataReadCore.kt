package tech.kzen.auto.server.objects.job.worker.data

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * Shared cursor drive for data-reading Workers. A pull wraps `hasNext` and `next` in one blocking boundary,
 * cursor handles transfer one-way across migration, and the effective output shape is fixed by the first part.
 */
object DataReadCore {
    const val schemaStrict = "strict"
    const val schemaSuperset = "superset"

    data class Pull(
        val hasItem: Boolean,
        val item: DataValue?
    )


    data class ShapeBaseline(
        val shape: DataShape,
        val origin: String
    )


    data class ShapeCandidate(
        val shape: DataShape,
        val attributes: Map<String, String>?,
        val origin: String
    )


    class DetachedCursor internal constructor(
        private var cursor: DataCursor?
    ): AutoCloseable {
        internal fun adopt(): DataCursor {
            val adopted = cursor
                ?: throw IllegalStateException("Detached data cursor was already adopted or closed")
            cursor = null
            return adopted
        }


        override fun close() {
            val closing = cursor
            cursor = null
            closing?.close()
        }
    }


    suspend fun open(
        context: DataContext,
        lookup: DataOpenerLookup,
        part: DataPart
    ): DataCursor {
        return lookup.openerFor(part.ref).open(context, part)
    }


    fun detach(cursor: DataCursor?): DetachedCursor? {
        return cursor?.let(::DetachedCursor)
    }


    fun adopt(detached: DetachedCursor?): DataCursor? {
        return detached?.adopt()
    }


    suspend fun close(control: JobControl, cursor: DataCursor?) {
        if (cursor != null) {
            control.runBlockingIo(cursor::close)
        }
    }


    fun closeFallback(cursor: DataCursor?) {
        cursor?.close()
    }


    suspend fun pull(control: JobControl, cursor: DataCursor): Pull {
        return control.runBlockingIo {
            if (cursor.hasNext()) {
                Pull(true, cursor.next())
            }
            else {
                Pull(false, null)
            }
        }
    }


    /**
     * Pulls and converts one item, then claims its durable position immediately before handing the message to
     * the channel. Keeping this ordering in the shared read core prevents ReadPartWorker and later readers from
     * copying the migration-sensitive claim-before-send sequence.
     *
     * @return false only when the cursor is exhausted; no claim or send occurs in that case.
     */
    suspend fun emitNext(
        control: JobControl,
        cursor: DataCursor,
        effectiveShape: ShapeBaseline,
        unitAttributes: Map<String, String>?,
        claimBeforeSend: () -> Unit,
        send: suspend (DataValue) -> Unit
    ): Boolean {
        val pull = pull(control, cursor)
        if (!pull.hasItem) {
            return false
        }

        val message = message(
            cursor.shape,
            effectiveShape,
            requireNotNull(pull.item),
            unitAttributes)
        claimBeforeSend()
        send(message)
        return true
    }


    suspend fun skipItems(control: JobControl, cursor: DataCursor, count: Long) {
        val skipped = skipAvailable(control, cursor, count)
        check(skipped == count) {
            "Unable to resume data cursor at item $count; exhausted at $skipped"
        }
    }


    /** Skips up to [count] items, returning early at this cursor's end so a caller can continue in the next part. */
    suspend fun skipAvailable(control: JobControl, cursor: DataCursor, count: Long): Long {
        var skipped = 0L
        while (skipped < count && pull(control, cursor).hasItem) {
            skipped += 1
        }
        return skipped
    }


    fun parts(unit: DataUnit, requestedRole: String, unitIndex: Number): List<DataPart> {
        if (requestedRole.isNotBlank()) {
            val selected = unit.partsOf(DataRole(requestedRole))
            check(selected.isNotEmpty()) {
                "Data unit $unitIndex has no readable role '$requestedRole'"
            }
            return selected
        }

        val roles = unit.parts.map { it.role.name }.distinct()
        check(roles.isNotEmpty()) {
            "Data unit $unitIndex has no readable roles"
        }
        check(roles.size == 1) {
            "Data unit $unitIndex has multiple readable roles: ${roles.joinToString()}"
        }
        return unit.partsOf(DataRole(roles.single()))
    }


    fun effectiveShape(
        cursorShape: DataShape,
        unitAttributes: Map<String, String>?,
        origin: String
    ): ShapeBaseline {
        if (unitAttributes == null) {
            return ShapeBaseline(cursorShape, origin)
        }

        val header = LegacyDataShapeBridge.headerOrNull(cursorShape)
            ?: throw IllegalStateException(
                "attributes=columns requires record data at $origin, found ${describe(cursorShape)}")
        val columnNames = header.values.map { it.text }.toSet()
        val collisions = unitAttributes.keys.filter { it in columnNames }
        check(collisions.isEmpty()) {
            "Data attributes collide with columns at $origin: ${collisions.joinToString()}"
        }

        val attributeHeader = HeaderListing.ofUnique(unitAttributes.keys.toList())
        return ShapeBaseline(
            LegacyDataShapeBridge.tabular(attributeHeader.append(header)),
            origin)
    }


    fun establishShape(
        baseline: ShapeBaseline?,
        candidate: ShapeBaseline
    ): ShapeBaseline {
        if (baseline == null) {
            return candidate
        }
        check(baseline.shape.itemType == candidate.shape.itemType) {
            "Data shape mismatch between ${baseline.origin} (${describe(baseline.shape)}) and " +
                "${candidate.origin} (${describe(candidate.shape)})"
        }
        return baseline
    }


    fun planShape(
        candidates: List<ShapeCandidate>,
        schemaMode: String
    ): ShapeBaseline {
        check(candidates.isNotEmpty()) { "No readable data parts" }
        if (schemaMode == schemaStrict) {
            var baseline: ShapeBaseline? = null
            for (candidate in candidates) {
                baseline = establishShape(
                    baseline,
                    effectiveShape(
                        candidate.shape,
                        candidate.attributes,
                        candidate.origin))
            }
            return requireNotNull(baseline)
        }
        check(schemaMode == schemaSuperset) { "Unknown schema mode: $schemaMode" }
        val flat = candidates.filter { it.shape.itemType.structural is DataType.Record }
        if (flat.size == candidates.size) {
            val headers = candidates.map { candidate ->
                LegacyDataShapeBridge.headerOrNull(candidate.shape)
                    ?: error("Flat-record shape at ${candidate.origin} is not a record")
            }
            val attributeNames = linkedSetOf<String>()
            candidates.forEach { attributeNames.addAll(it.attributes?.keys.orEmpty()) }
            val dataLabels = linkedSetOf<tech.kzen.auto.common.data.schema.HeaderLabel>()
            headers.forEach { dataLabels.addAll(it.values) }
            val dataNames = dataLabels.mapTo(linkedSetOf()) { it.text }
            val collisions = attributeNames.filter { it in dataNames }
            check(collisions.isEmpty()) {
                "Data attributes collide with columns across the selected data: ${collisions.joinToString()}"
            }
            val attributes = HeaderListing.ofUnique(attributeNames.toList())
            val combined = attributes.append(HeaderListing(dataLabels.toList()))
            val firstShape = candidates.first().shape
            return ShapeBaseline(
                DataShape(
                    DataContract(DataType.Record(combined.values.map { label ->
                        DataField(
                            FieldId(label.text, label.occurrence),
                            DataType.Scalar(ScalarKind.Text),
                            optional = true)
                    })),
                    firstShape.provenance,
                    firstShape.stability,
                    firstShape.diagnostics),
                candidates.first().origin)
        }

        check(candidates.none { it.shape.itemType.structural is DataType.Record }) {
            "Mixed record and non-record data beginning at ${candidates.first().origin}"
        }
        val first = candidates.first().shape
        val mismatch = candidates.firstOrNull { it.shape.itemType != first.itemType }
        check(mismatch == null) {
            "Payload shape mismatch between ${candidates.first().origin} (${first.itemType}) and " +
                    "${mismatch?.origin} (${mismatch?.shape?.itemType})"
        }
        val attributes = candidates.firstNotNullOfOrNull { it.attributes }
        check(attributes == null) {
            "attributes=columns requires flat-record data at ${candidates.first().origin}, " +
                    "found payload ${first.itemType}"
        }
        return ShapeBaseline(first, candidates.first().origin)
    }


    fun message(
        cursorShape: DataShape,
        effectiveShape: ShapeBaseline,
        item: DataValue,
        unitAttributes: Map<String, String>?
    ): DataValue {
        if (cursorShape.itemType.structural !is DataType.Record) {
            return item
        }

        val record = item.access as? FlatFileRecord
            ?: throw IllegalStateException(
                "Tabular cursor at ${effectiveShape.origin} emitted " +
                    item.access::class.qualifiedName +
                    "; expected ${FlatFileRecord::class.qualifiedName}")
        val candidateRecord =
            if (unitAttributes == null) {
                record
            }
            else {
                FlatFileRecord.of(unitAttributes.values + record.toList())
            }
        val cursorHeader = LegacyDataShapeBridge.headerOrNull(cursorShape)
            ?: throw IllegalStateException(
                "Flat-record cursor at ${effectiveShape.origin} has non-record shape ${cursorShape.itemType}")
        val candidateHeader =
            if (unitAttributes == null) {
                cursorHeader
            }
            else {
                HeaderListing.ofUnique(unitAttributes.keys.toList()).append(cursorHeader)
            }
        val header = LegacyDataShapeBridge.headerOrNull(effectiveShape.shape)
            ?: error("Effective flat-record shape is not a record: ${effectiveShape.shape}")
        if (unitAttributes == null && cursorHeader == header) {
            return item
        }
        val states = header.values.map { label ->
            val index = candidateHeader.values.indexOf(label)
            if (index == -1) DataState.Absent else DataState.Present
        }
        val projected = FlatFileRecord.of(header.values.mapIndexed { index, label ->
            val candidateIndex = candidateHeader.values.indexOf(label)
            if (states[index] == DataState.Absent) "" else candidateRecord.getString(candidateIndex)
        })
        return JobDataValues.projectedRecord(effectiveShape.shape.itemType, projected, states)
    }


    private fun describe(shape: DataShape): String =
        LegacyDataShapeBridge.headerOrNull(shape)?.let { "record ${it.render()}" }
            ?: "value ${shape.itemType.structural}"

}
