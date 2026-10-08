package tech.kzen.auto.server.objects.job.value

import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.auto.server.data.read.FlatRecordBacked
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
    override val flatRecord: FlatFileRecord,
    private val header: FlatRecordHeader,
    val states: List<DataState>?,
    private val nativeRoot: Any?
):
    ValueAccess by flatRecord,
    FlatRecordBacked
{
    override fun contract(node: DataNode): DataContract =
        if (node.token == 0L) header.contract else header.contractAt(fieldIndex(node))

    override fun state(node: DataNode): DataState =
        if (node.token == 0L) DataState.Present else states?.get(fieldIndex(node)) ?: DataState.Present

    override fun scalar(node: DataNode): ScalarExecutionValue {
        requirePresent(node)
        return flatRecord.scalar(node)
    }

    override fun readBoolean(node: DataNode): Boolean {
        requirePresent(node)
        return flatRecord.readBoolean(node)
    }

    override fun readLong(node: DataNode): Long {
        requirePresent(node)
        return flatRecord.readLong(node)
    }

    override fun readDouble(node: DataNode): Double {
        requirePresent(node)
        return flatRecord.readDouble(node)
    }

    override fun readText(node: DataNode): String {
        requirePresent(node)
        return flatRecord.readText(node)
    }

    override fun readBinary(node: DataNode): ByteArray {
        requirePresent(node)
        return flatRecord.readBinary(node)
    }

    override fun native(node: DataNode): Any {
        if (node.token == 0L && nativeRoot != null) return nativeRoot
        return flatRecord.native(node)
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
