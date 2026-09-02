package tech.kzen.auto.server.objects.job.worker.data

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.read.CursorAdoptionIdentity
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.data.read.OperationalDataCursor
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.auto.plugin.model.record.FlatFileRecord


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
        private var cursor: DataCursor?,
        private val identity: CursorAdoptionIdentity?
    ): AutoCloseable {
        internal fun adopt(expectedIdentity: CursorAdoptionIdentity?): DataCursor {
            val adopted = cursor
                ?: throw IllegalStateException("Detached data cursor was already adopted or closed")
            if (identity == null || expectedIdentity == null || !identity.compatibleWith(expectedIdentity)) {
                cursor = null
                adopted.close()
                throw IllegalStateException(
                    "Detached data cursor is incompatible with the current part or read policy: " +
                        "captured=$identity, current=$expectedIdentity")
            }
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
        return cursor?.let {
            DetachedCursor(it, (it as? OperationalDataCursor)?.adoptionIdentity)
        }
    }


    fun adopt(
        detached: DetachedCursor?,
        expectedIdentity: CursorAdoptionIdentity? = null
    ): DataCursor? {
        return detached?.adopt(expectedIdentity)
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

        val record = cursorShape.itemType.structural as DataType.Record
        val attributeFields = unitAttributes.keys.map { name ->
            DataField(FieldId(name), DataType.Scalar(ScalarKind.Text))
        }
        val contract = combineContract(
            attributeFields.map { ContractField(it, DataContract(it.type), origin) } +
                contractFields(cursorShape.itemType, record.fields, origin),
            record.nullable)
        return ShapeBaseline(
            DataShape(
                contract,
                cursorShape.provenance,
                cursorShape.stability,
                cursorShape.diagnostics),
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
            val attributeNames = linkedSetOf<String>()
            candidates.forEach { attributeNames.addAll(it.attributes?.keys.orEmpty()) }
            val dataNames = candidates.flatMap { candidate ->
                val record = candidate.shape.itemType.structural as DataType.Record
                record.fields.map { it.id.name }
            }.toSet()
            val collisions = attributeNames.filter { it in dataNames }
            check(collisions.isEmpty()) {
                "Data attributes collide with columns across the selected data: ${collisions.joinToString()}"
            }

            val mergedAttributes = attributeNames.map { name ->
                val id = FieldId(name)
                ContractField(
                    DataField(
                        id,
                        DataType.Scalar(ScalarKind.Text),
                        optional = candidates.any { name !in it.attributes.orEmpty() }),
                    DataContract(DataType.Scalar(ScalarKind.Text)),
                    candidates.first { name in it.attributes.orEmpty() }.origin)
            }
            val mergedData = mutableListOf<MergedField>()
            var recordNullable: Boolean? = null
            var recordNullableOrigin: String? = null
            for (candidate in candidates) {
                val contract = candidate.shape.itemType
                val record = contract.structural as DataType.Record
                val previousNullable = recordNullable
                check(previousNullable == null || previousNullable == record.nullable) {
                    "Record nullability mismatch between $recordNullableOrigin ($previousNullable) and " +
                        "${candidate.origin} (${record.nullable})"
                }
                recordNullable = record.nullable
                recordNullableOrigin = recordNullableOrigin ?: candidate.origin
                for (field in contractFields(contract, record.fields, candidate.origin)) {
                    val existing = mergedData.firstOrNull { it.contractField.field.id == field.field.id }
                    if (existing == null) {
                        val insertAfter = mergedData.indexOfLast {
                            it.contractField.field.id.name == field.field.id.name
                        }
                        if (insertAfter == -1) {
                            mergedData.add(MergedField(field, 1))
                        }
                        else {
                            mergedData.add(insertAfter + 1, MergedField(field, 1))
                        }
                    }
                    else {
                        check(existing.contractField.contract == field.contract) {
                            "Data field ${field.field.id} contract mismatch between " +
                                "${existing.contractField.origin} (${existing.contractField.contract}) and " +
                                "${field.origin} (${field.contract})"
                        }
                        existing.presentCount += 1
                        existing.optional = existing.optional || field.field.optional
                    }
                }
            }
            val mergedFields = mergedData.map { merged ->
                val field = merged.contractField.field.copy(
                    optional = merged.optional || merged.presentCount != candidates.size)
                merged.contractField.copy(field = field)
            }
            val firstShape = candidates.first().shape
            return ShapeBaseline(
                DataShape(
                    combineContract(mergedAttributes + mergedFields, recordNullable == true),
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

        if (unitAttributes == null && cursorShape.itemType == effectiveShape.shape.itemType) {
            return item
        }

        val cursorRecord = cursorShape.itemType.structural as DataType.Record
        val effectiveRecord = effectiveShape.shape.itemType.structural as? DataType.Record
            ?: error("Effective flat-record shape is not a record: ${effectiveShape.shape}")
        val cursorFields = cursorRecord.fields.associateBy { it.id }
        val cursorFieldNames = cursorFields.keys.mapTo(mutableSetOf()) { it.name }
        val values = ArrayList<String>(effectiveRecord.fields.size)
        val states = ArrayList<DataState>(effectiveRecord.fields.size)
        for (field in effectiveRecord.fields) {
            val attribute = unitAttributes?.get(field.id.name)
                .takeIf { field.id.occurrence == 0 && field.id.name !in cursorFieldNames }
            if (attribute != null) {
                values.add(attribute)
                states.add(DataState.Present)
                continue
            }
            if (field.id !in cursorFields) {
                values.add("")
                states.add(DataState.Absent)
                continue
            }
            val node = item.access.field(item.root, field.id)
            val state = item.access.state(node)
            states.add(state)
            values.add(if (state == DataState.Present) renderScalar(item, node, field) else "")
        }
        val projected = FlatFileRecord.of(values)
        return JobDataValues.projectedRecord(effectiveShape.shape.itemType, projected, states)
    }


    private fun renderScalar(
        item: DataValue,
        node: DataNode,
        field: DataField
    ): String {
        check(field.type is DataType.Scalar) {
            "Data field ${field.id} requires scalar materialization, found ${field.type}"
        }
        return scalarText(item.access.scalar(node))
    }


    private fun scalarText(value: ScalarExecutionValue): String = when (value) {
        is TextExecutionValue -> value.value
        is BooleanExecutionValue -> value.value.toString()
        is LongExecutionValue -> value.value.toString()
        is NumberExecutionValue -> value.value.toString()
        is BinaryExecutionValue -> value.asBase64()
        else -> throw IllegalStateException("Unsupported scalar materialization: ${value::class.qualifiedName}")
    }


    private fun contractFields(
        contract: DataContract,
        fields: List<DataField>,
        origin: String
    ): List<ContractField> = fields.map { field ->
        ContractField(
            field,
            contract.child(DataPathSegment.Field(field.id)),
            origin)
    }


    private fun combineContract(
        fields: List<ContractField>,
        nullable: Boolean
    ): DataContract {
        val nativeByPath = linkedMapOf<DataTypePath, tech.kzen.lib.common.model.structure.metadata.TypeMetadata>()
        for (field in fields) {
            val fieldPrefix = DataTypePath(listOf(DataPathSegment.Field(field.field.id)))
            for ((path, metadata) in field.contract.nativeByPath) {
                nativeByPath[DataTypePath(fieldPrefix.segments + path.segments)] = metadata
            }
        }
        return DataContract(DataType.Record(fields.map { it.field }, nullable), nativeByPath)
    }


    private data class ContractField(
        val field: DataField,
        val contract: DataContract,
        val origin: String
    )


    private data class MergedField(
        val contractField: ContractField,
        var presentCount: Int,
        var optional: Boolean = contractField.field.optional
    )


    private fun describe(shape: DataShape): String =
        LegacyDataShapeBridge.headerOrNull(shape)?.let { "record ${it.render()}" }
            ?: "value ${shape.itemType.structural}"

}
