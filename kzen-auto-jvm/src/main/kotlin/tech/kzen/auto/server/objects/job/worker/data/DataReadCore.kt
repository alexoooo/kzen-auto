package tech.kzen.auto.server.objects.job.worker.data

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.objects.job.worker.JobMessage


/**
 * Shared cursor drive for data-reading Workers. A pull wraps `hasNext` and `next` in one blocking boundary,
 * cursor handles transfer one-way across migration, and the effective output shape is fixed by the first part.
 */
object DataReadCore {
    const val schemaStrict = "strict"
    const val schemaSuperset = "superset"

    data class Pull(
        val hasItem: Boolean,
        val item: Any?
    )


    data class ShapeBaseline(
        val shape: DataShape?,
        val origin: String
    )


    data class ShapeCandidate(
        val shape: DataShape?,
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
        send: suspend (JobMessage) -> Unit
    ): Boolean {
        val pull = pull(control, cursor)
        if (!pull.hasItem) {
            return false
        }

        val message = message(cursor.shape, effectiveShape, pull.item, unitAttributes)
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
        cursorShape: DataShape?,
        unitAttributes: Map<String, String>?,
        origin: String
    ): ShapeBaseline {
        if (unitAttributes == null) {
            return ShapeBaseline(cursorShape, origin)
        }

        val tabular = cursorShape as? DataShape.Tabular
            ?: throw IllegalStateException(
                "attributes=columns requires tabular data at $origin, found ${describe(cursorShape)}")
        val columnNames = tabular.header.values.map { it.text }.toSet()
        val collisions = unitAttributes.keys.filter { it in columnNames }
        check(collisions.isEmpty()) {
            "Data attributes collide with columns at $origin: ${collisions.joinToString()}"
        }

        val attributeHeader = HeaderListing.ofUnique(unitAttributes.keys.toList())
        return ShapeBaseline(
            DataShape.Tabular(attributeHeader.append(tabular.header)), origin)
    }


    fun establishShape(
        baseline: ShapeBaseline?,
        candidate: ShapeBaseline
    ): ShapeBaseline {
        if (baseline == null) {
            return candidate
        }
        check(baseline.shape == candidate.shape) {
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
                    effectiveShape(candidate.shape, candidate.attributes, candidate.origin))
            }
            return requireNotNull(baseline)
        }
        check(schemaMode == schemaSuperset) { "Unknown schema mode: $schemaMode" }
        val unknown = candidates.firstOrNull { it.shape == null }
        check(unknown == null) { "Unable to inspect data shape at ${unknown?.origin}" }

        val tabular = candidates.mapNotNull { it.shape as? DataShape.Tabular }
        if (tabular.size == candidates.size) {
            val attributeNames = linkedSetOf<String>()
            candidates.forEach { attributeNames.addAll(it.attributes?.keys.orEmpty()) }
            val dataLabels = linkedSetOf<tech.kzen.auto.common.data.schema.HeaderLabel>()
            tabular.forEach { dataLabels.addAll(it.header.values) }
            val dataNames = dataLabels.mapTo(linkedSetOf()) { it.text }
            val collisions = attributeNames.filter { it in dataNames }
            check(collisions.isEmpty()) {
                "Data attributes collide with columns across the selected data: ${collisions.joinToString()}"
            }
            val attributes = HeaderListing.ofUnique(attributeNames.toList())
            return ShapeBaseline(
                DataShape.Tabular(attributes.append(HeaderListing(dataLabels.toList()))),
                candidates.first().origin)
        }

        val payload = candidates.mapNotNull { it.shape as? DataShape.Payload }
        check(payload.size == candidates.size) {
            "Mixed tabular and payload data between ${candidates.first().origin} and " +
                candidates.first { it.shape!!::class != candidates.first().shape!!::class }.origin
        }
        val first = payload.first()
        val mismatch = candidates.zip(payload).firstOrNull { it.second != first }
        check(mismatch == null) {
            "Payload shape mismatch between ${candidates.first().origin} (${first.type}) and " +
                "${mismatch?.first?.origin} (${mismatch?.second?.type})"
        }
        val attributes = candidates.firstNotNullOfOrNull { it.attributes }
        check(attributes == null) {
            "attributes=columns requires tabular data at ${candidates.first().origin}, found payload ${first.type}"
        }
        return ShapeBaseline(first, candidates.first().origin)
    }


    fun message(
        cursorShape: DataShape?,
        effectiveShape: ShapeBaseline,
        item: Any?,
        unitAttributes: Map<String, String>?
    ): JobMessage {
        if (cursorShape !is DataShape.Tabular) {
            return JobMessage.ofPayload(item)
        }

        val record = item as? FlatFileRecord
            ?: throw IllegalStateException(
                "Tabular cursor at ${effectiveShape.origin} emitted " +
                    (item?.let { it::class.qualifiedName } ?: "null") +
                    "; expected ${FlatFileRecord::class.qualifiedName}")
        val candidateRecord =
            if (unitAttributes == null) {
                record
            }
            else {
                FlatFileRecord.of(unitAttributes.values + record.toList())
            }
        val candidateHeader =
            if (unitAttributes == null) {
                cursorShape.header
            }
            else {
                HeaderListing.ofUnique(unitAttributes.keys.toList()).append(cursorShape.header)
            }
        val header = (effectiveShape.shape as DataShape.Tabular).header
        val projected = FlatFileRecord.of(header.values.map { label ->
            val index = candidateHeader.values.indexOf(label)
            if (index == -1) DataShape.missingCellValue else candidateRecord.getString(index)
        })
        return JobMessage.ofFlat(header, projected)
    }


    private fun describe(shape: DataShape?): String {
        return when (shape) {
            null -> "unknown payload"
            is DataShape.Payload -> "payload ${shape.type}"
            is DataShape.Tabular -> "columns ${shape.header.render()}"
        }
    }
}
