package tech.kzen.auto.server.objects.job.value

import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.ValueAccess


/** Materialized record plus a retained native root and optional per-field absence/null state. */
internal class ProjectedRecordValueAccess(
    val record: FlatFileRecord,
    private val header: FlatRecordHeader,
    val states: List<DataState>?,
    private val nativeRoot: Any?
): ValueAccess by record {
    override fun contract(node: DataNode): DataContract =
        if (node.token == 0L) header.contract else header.contractAt(fieldIndex(node))

    override fun state(node: DataNode): DataState =
        if (node.token == 0L) DataState.Present else states?.get(fieldIndex(node)) ?: DataState.Present

    override fun scalar(node: DataNode): ScalarExecutionValue {
        requirePresent(node)
        return record.scalar(node)
    }

    override fun readBoolean(node: DataNode): Boolean {
        requirePresent(node)
        return record.readBoolean(node)
    }

    override fun readLong(node: DataNode): Long {
        requirePresent(node)
        return record.readLong(node)
    }

    override fun readDouble(node: DataNode): Double {
        requirePresent(node)
        return record.readDouble(node)
    }

    override fun readText(node: DataNode): String {
        requirePresent(node)
        return record.readText(node)
    }

    override fun readBinary(node: DataNode): ByteArray {
        requirePresent(node)
        return record.readBinary(node)
    }

    override fun native(node: DataNode): Any {
        if (node.token == 0L && nativeRoot != null) return nativeRoot
        return record.native(node)
    }

    private fun fieldIndex(node: DataNode): Int {
        val index = node.token.toInt() - 1
        val fieldCount = (header.contract.structural as DataType.Record).fields.size
        require(index in 0 until fieldCount) { "Node ${node.token} does not belong to this projected record" }
        return index
    }

    private fun requirePresent(node: DataNode) {
        val state = state(node)
        if (state != DataState.Present) {
            throw DataAccessException(DataProblem(
                DataProblem.invalidState,
                "Scalar read requires a present projected field, found $state"))
        }
    }
}
