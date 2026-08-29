package tech.kzen.auto.plugin.model.record

import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.VariantId
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.ValueAccess


/**
 * Kotlin bridge that lets the Java [FlatFileRecord] implement the inline-node [ValueAccess] ABI directly.
 * A root is token zero; field token `n + 1` is the existing flat-array index and creates no wrapper.
 */
abstract class FlatRecordValueAccess: ValueAccess {
    companion object {
        private val numberScratch = ThreadLocal.withInitial { LongArray(2) }
    }

    private var dataHeader: FlatRecordHeader? = null

    fun attachHeader(header: FlatRecordHeader) {
        dataHeader = header
        if (dataFieldCount() != 0 && dataFieldCount() != recordType().fields.size) {
            invalid("Flat record has ${dataFieldCount()} fields but header has ${recordType().fields.size}")
        }
    }

    protected fun attachedHeaderOrNull(): FlatRecordHeader? = dataHeader
    protected fun replaceAttachedHeader(header: FlatRecordHeader?) {
        dataHeader = header
    }

    protected abstract fun dataFieldCount(): Int
    protected abstract fun dataText(fieldIndex: Int): String
    protected abstract fun dataDoubleOrNan(fieldIndex: Int, scratch: LongArray): Double

    override fun contract(node: DataNode): DataContract =
        if (node.token == 0L) {
            header().contract
        }
        else {
            header().contractAt(fieldIndex(node))
        }

    override fun state(node: DataNode): DataState {
        requireNode(node)
        return DataState.Present
    }

    override fun field(node: DataNode, field: FieldId): DataNode {
        if (node.token != 0L) invalid("field is valid only for the flat-record root")
        val index = header().indexOf(field.name, field.occurrence)
        if (index < 0 || index >= dataFieldCount()) invalid("Unknown flat-record field '$field'")
        return DataNode(index.toLong() + 1)
    }

    override fun scalar(node: DataNode): ScalarExecutionValue =
        when (scalarType(node).kind) {
            ScalarKind.Boolean -> BooleanExecutionValue.of(readBoolean(node))
            is ScalarKind.Integer -> LongExecutionValue(readLong(node))
            ScalarKind.Decimal -> TextExecutionValue(dataText(fieldIndex(node)))
            is ScalarKind.Floating -> NumberExecutionValue(readDouble(node))
            ScalarKind.Text,
            ScalarKind.Date,
            ScalarKind.Time,
            ScalarKind.Instant,
            ScalarKind.Duration,
            ScalarKind.Uuid -> TextExecutionValue(dataText(fieldIndex(node)))
            ScalarKind.Binary -> invalid("Flat records do not expose binary fields")
        }

    override fun readBoolean(node: DataNode): Boolean {
        requireKind(node, ScalarKind.Boolean)
        return when (val text = dataText(fieldIndex(node))) {
            "true" -> true
            "false" -> false
            else -> invalid("Flat field '$text' is not a canonical Boolean")
        }
    }

    override fun readLong(node: DataNode): Long {
        if (scalarType(node).kind !is ScalarKind.Integer) invalid("Integer scalar required")
        return dataText(fieldIndex(node)).toLongOrNull()
            ?: invalid("Flat field cannot be represented exactly as Long")
    }

    override fun readDouble(node: DataNode): Double {
        val kind = scalarType(node).kind
        if (kind !is ScalarKind.Floating && kind !is ScalarKind.Integer && kind != ScalarKind.Decimal) {
            invalid("Numeric scalar required")
        }
        return dataDoubleOrNan(fieldIndex(node), numberScratch.get()).takeIf { it.isFinite() }
            ?: invalid("Flat field cannot be represented as a finite Double")
    }

    override fun readText(node: DataNode): String {
        requireKind(node, ScalarKind.Text)
        return dataText(fieldIndex(node))
    }

    override fun activeVariant(node: DataNode): VariantId = invalid("Flat records are not union nodes")
    override fun selected(node: DataNode): DataNode = invalid("Flat records are not union nodes")
    override fun entry(node: DataNode, key: ScalarExecutionValue): DataNode =
        invalid("Flat records are not mapping nodes")
    override fun element(node: DataNode, index: Int): DataNode = invalid("Flat records are not listing nodes")
    override fun size(node: DataNode): Int = invalid("Flat records are not container nodes")
    override fun keyAt(node: DataNode, index: Int): ScalarExecutionValue =
        invalid("Flat records are not mapping nodes")
    override fun readBinary(node: DataNode): ByteArray = invalid("Flat records do not expose binary fields")

    override fun native(node: DataNode): Any {
        requireNode(node)
        if (contract(node).nativeByPath[DataTypePath.root] == null) {
            invalid("Flat-record node has no native facet")
        }
        return this
    }

    private fun recordType(): DataType.Record = header().contract.structural as DataType.Record
    private fun scalarType(node: DataNode): DataType.Scalar =
        contract(node).structural as? DataType.Scalar ?: invalid("Flat field is not scalar")
    private fun requireKind(node: DataNode, kind: ScalarKind) {
        if (scalarType(node).kind != kind) invalid("$kind scalar required")
    }
    private fun fieldIndex(node: DataNode): Int {
        requireNode(node)
        if (node.token == 0L) invalid("Operation requires a flat-record field")
        return node.token.toInt() - 1
    }
    private fun requireNode(node: DataNode) {
        if (node.token < 0 || node.token > dataFieldCount().toLong()) {
            invalid("Data node ${node.token} does not belong to this flat record")
        }
    }
    private fun header(): FlatRecordHeader = dataHeader
        ?: invalid("Flat record has no attached header")
    private fun invalid(message: String): Nothing =
        throw DataAccessException(DataProblem(DataProblem.invalidOperation, message))
}
