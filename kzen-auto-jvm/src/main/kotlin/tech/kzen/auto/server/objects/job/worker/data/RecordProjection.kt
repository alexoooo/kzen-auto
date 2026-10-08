package tech.kzen.auto.server.objects.job.worker.data

import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.auto.server.data.read.FlatRecordBacked
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * How the items of one cursor become messages of its [effectiveShape] ([DataReadCore.message]), planned once per
 * file: the column mapping, the target header and the cell states shared by rows without a null. A flat item's
 * text cells are copied as character ranges; other cells render through their scalar.
 *
 * Holds a number-parse scratch, so one Worker uses it at a time.
 */
class RecordProjection internal constructor(
    val cursorShape: DataShape,
    val effectiveShape: DataReadCore.ShapeBaseline,
    val unitAttributes: Map<String, String>?
) {
    private sealed interface Cell {
        class Attribute(val text: String): Cell
        data object Absent: Cell
        class Source(val field: DataField, val index: Int, val copiesText: Boolean): Cell
    }


    private val cells: List<Cell>?
    private val header: FlatRecordHeader?
    private val presentStates: List<DataState>?
    private val numberScratch = LongArray(2)


    init {
        val passThrough = cursorShape.itemType.structural !is DataType.Record ||
            unitAttributes == null && cursorShape.itemType == effectiveShape.shape.itemType
        if (passThrough) {
            cells = null
            header = null
            presentStates = null
        }
        else {
            val cursorRecord = cursorShape.itemType.structural as DataType.Record
            val effectiveRecord = effectiveShape.shape.itemType.structural as? DataType.Record
                ?: error("Effective flat-record shape is not a record: ${effectiveShape.shape}")
            val cursorIndex = cursorRecord.fields.withIndex().associate { (index, field) -> field.id to index }
            val cursorFieldNames = cursorIndex.keys.mapTo(mutableSetOf()) { it.name }
            cells = effectiveRecord.fields.map { field ->
                val attribute = unitAttributes?.get(field.id.name)
                    .takeIf { field.id.occurrence == 0 && field.id.name !in cursorFieldNames }
                val index = cursorIndex[field.id]
                when {
                    attribute != null -> Cell.Attribute(attribute)
                    index == null -> Cell.Absent
                    else -> Cell.Source(field, index, copiesText(field))
                }
            }
            header = FlatRecordHeader(effectiveShape.shape.itemType)
            presentStates = cells.map { if (it == Cell.Absent) DataState.Absent else DataState.Present }
        }
    }


    /** True when this plan was made for these arguments, so a reader keeps it for the rest of the file. */
    fun plans(
        cursorShape: DataShape,
        effectiveShape: DataReadCore.ShapeBaseline,
        unitAttributes: Map<String, String>?
    ): Boolean =
        this.cursorShape === cursorShape &&
            this.effectiveShape === effectiveShape &&
            this.unitAttributes === unitAttributes


    fun message(item: DataValue): DataValue {
        val cells = cells
            ?: return item
        val access = item.access

        // A flat item of the cursor's own contract: field n is node n + 1, its text the cell's text
        val flat = when (access) {
            is FlatRecordBacked -> access.flatRecord
            is FlatFileRecord -> access
            else -> null
        }?.takeIf { access.contract(item.root) === cursorShape.itemType }

        val record = FlatFileRecord(flat?.fieldContentLength() ?: 0, cells.size)
        var states: MutableList<DataState>? = null
        for ((index, cell) in cells.withIndex()) {
            when (cell) {
                is Cell.Attribute -> record.add(cell.text)
                Cell.Absent -> record.add("")
                is Cell.Source -> {
                    val node =
                        if (flat != null) {
                            DataNode(cell.index + 1L)
                        }
                        else {
                            access.field(item.root, cell.field.id)
                        }
                    val state = access.state(node)
                    if (state != DataState.Present) {
                        val changed = states ?: requireNotNull(presentStates).toMutableList()
                        changed[index] = state
                        states = changed
                        record.add("")
                    }
                    else if (flat != null && cell.copiesText) {
                        val start = flat.contentStart(cell.index)
                        record.add(flat.fieldContentsUnsafe(), start, flat.contentEnd(cell.index) - start)
                    }
                    else {
                        record.add(renderScalar(item, node, cell.field))
                    }
                }
            }
        }
        record.populateCaches(numberScratch)
        return JobDataValues.projectedRecord(
            requireNotNull(header), record, states ?: requireNotNull(presentStates))
    }


    // A scalar of these kinds renders as its flat text unchanged (FlatRecordValueAccess.scalar)
    private fun copiesText(field: DataField): Boolean {
        val scalar = field.type as? DataType.Scalar
            ?: return false
        return when (scalar.kind) {
            ScalarKind.Text,
            ScalarKind.Decimal,
            ScalarKind.Date,
            ScalarKind.Time,
            ScalarKind.Instant,
            ScalarKind.Duration,
            ScalarKind.Uuid -> true
            else -> false
        }
    }


    private fun renderScalar(item: DataValue, node: DataNode, field: DataField): String {
        check(field.type is DataType.Scalar) {
            "Data field ${field.id} requires scalar materialization, found ${field.type}"
        }
        return DataReadCore.scalarText(item.access.scalar(node))
    }
}
