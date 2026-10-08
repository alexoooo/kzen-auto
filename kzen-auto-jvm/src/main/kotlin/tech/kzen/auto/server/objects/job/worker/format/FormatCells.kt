package tech.kzen.auto.server.objects.job.worker.format

import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec.WriterColumn
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatFileRecordField
import tech.kzen.auto.server.data.read.FlatRecordBacked
import tech.kzen.auto.server.data.write.RecordCells
import tech.kzen.auto.server.objects.job.value.ColumnProjection
import tech.kzen.auto.server.objects.job.value.ColumnProjectionDescriptor
import tech.kzen.auto.server.objects.job.worker.WriterColumns
import tech.kzen.auto.server.objects.job.worker.data.DataReadCore
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueAccess


/**
 * The cells of the value last [bind]ed, in the order of [columns] over values of [contract]. A payload backed by a
 * flat record ([FlatRecordBacked], or a [FlatFileRecord] itself, as Parse's rows are) is read in place through one
 * reused view, so reading it allocates nothing; any other payload renders each scalar's text
 * ([DataReadCore.scalarText]). A null field is a null cell; an absent one (a superset's column that the value's own
 * part lacks) is empty, as reading leaves it. A path column's text is null when its leaf is null or unreachable.
 */
internal class FormatCells(
    private val columns: WriterColumns,
    private val contract: DataContract
): RecordCells {
    //-----------------------------------------------------------------------------------------------------------------
    // The payload's columns, when the cells or the name read any: a record payload always offers them
    private val payload: ColumnProjectionDescriptor? = contract.payload().let {
        if (readsPayload() || it.structural is DataType.Record) ColumnProjectionDescriptor.from(it) else null
    }

    private val payloadSize = payload?.columns?.size ?: 0

    // Each cell's source: a payload column's index, or -(n + 1) for the n-th path column
    private val layout: IntArray = layout()

    private val hasPaths = layout.any { it < 0 }

    private var value: DataValue? = null
    private var flat: FlatFileRecord? = null
    private var projection: ColumnProjection? = null
    private var pathValues: Array<String?>? = null

    // The payload columns' field tokens in a flat-backed value: the same for every value of one payload contract
    private var tokensFor: DataContract? = null
    private val tokens = LongArray(payloadSize)
    private val view = FlatFileRecordField()


    //-----------------------------------------------------------------------------------------------------------------
    override val size: Int
        get() = layout.size


    /** The payload column read by a name placeholder `${[name]}`, or -1 when no payload column has that name. */
    fun payloadColumn(name: String): Int {
        val columns = payload?.columns
            ?: return -1
        val field = FieldId(name)
        return columns.indexOfFirst { it.field == field }
    }


    fun bind(element: DataValue) {
        value = element
        if (hasPaths) {
            pathValues = columns.pathValues(element, contract)
        }
        val descriptor = payload
            ?: return

        val backing = flatBacking(element.access)
        flat = backing
        if (backing == null) {
            projection = descriptor.bind(element)
            return
        }
        projection = null
        val elementContract = element.payloadContract
        if (elementContract !== tokensFor) {
            val bound = descriptor.bind(element)
            for (column in 0 until payloadSize) {
                tokens[column] = bound.node(column).token
            }
            tokensFor = elementContract
        }
    }


    override fun isNull(index: Int): Boolean {
        val source = layout[index]
        return if (source >= 0) payloadState(source) == DataState.Null
            else checkNotNull(pathValues)[-source - 1] == null
    }


    override fun text(index: Int): CharSequence {
        val source = layout[index]
        return if (source >= 0) payloadText(source)
            else checkNotNull(checkNotNull(pathValues)[-source - 1])
    }


    fun payloadIsNull(column: Int): Boolean =
        payloadState(column) == DataState.Null


    /** The text of a non-null payload [column]; a reused view, valid until the next read. */
    fun payloadText(column: Int): CharSequence {
        if (payloadState(column) == DataState.Absent) {
            return ""
        }
        val backing = flat
        if (backing != null) {
            view.selectHostField(backing, (tokens[column] - 1).toInt())
            return view
        }
        return DataReadCore.scalarText(checkNotNull(projection).scalar(column))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun readsPayload(): Boolean =
        columns.spec.columns.isEmpty() || WriterColumn.PayloadFields in columns.spec.columns


    private fun layout(): IntArray {
        val spec = columns.spec.columns
        if (spec.isEmpty()) {
            return IntArray(payloadSize) { it }
        }
        val sources = ArrayList<Int>()
        var pathIndex = 0
        for (column in spec) {
            when (column) {
                WriterColumn.PayloadFields -> repeat(payloadSize) { sources += it }
                is WriterColumn.Path -> sources += -(++pathIndex)
            }
        }
        return sources.toIntArray()
    }


    private fun payloadState(column: Int): DataState {
        if (flat == null) {
            return checkNotNull(projection).state(column)
        }
        return checkNotNull(value).access.state(DataNode(tokens[column]))
    }


    private fun flatBacking(access: ValueAccess): FlatFileRecord? =
        when (access) {
            is FlatRecordBacked -> access.flatRecord
            is FlatFileRecord -> access
            else -> null
        }
}
