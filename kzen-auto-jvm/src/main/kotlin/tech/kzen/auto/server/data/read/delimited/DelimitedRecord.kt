package tech.kzen.auto.server.data.read.delimited

import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.data.read.FlatRecordBacked
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.VariantId
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueAccess


/**
 * One delimited row: its cells in [backing], read through its null flags (none when [nulls] is null). The row is
 * its own [access], so a row is the record, this access and its [value].
 */
class DelimitedRecord internal constructor(
    val backing: FlatFileRecord,
    private val nulls: BooleanArray?
):
    ValueAccess by backing,
    FlatRecordBacked
{
    val access: ValueAccess
        get() = this

    val value = DataValue(this, DataNode(0))

    override val flatRecord: FlatFileRecord
        get() = backing


    override fun state(node: DataNode): DataState {
        if (node.token == 0L) return DataState.Present
        val index = node.token.toInt() - 1
        if (nulls == null || index !in nulls.indices) return backing.state(node)
        return if (nulls[index]) DataState.Null else DataState.Present
    }

    override fun scalar(node: DataNode): ScalarExecutionValue = present(node) { backing.scalar(node) }
    override fun readBoolean(node: DataNode): Boolean = present(node) { backing.readBoolean(node) }
    override fun readLong(node: DataNode): Long = present(node) { backing.readLong(node) }
    override fun readDouble(node: DataNode): Double = present(node) { backing.readDouble(node) }
    override fun readText(node: DataNode): String = present(node) { backing.readText(node) }
    override fun readBinary(node: DataNode): ByteArray = present(node) { backing.readBinary(node) }
    override fun activeVariant(node: DataNode): VariantId = present(node) { backing.activeVariant(node) }
    override fun selected(node: DataNode): DataNode = present(node) { backing.selected(node) }

    private inline fun <T> present(node: DataNode, action: () -> T): T {
        if (state(node) == DataState.Null) {
            throw DataAccessException(DataProblem(DataProblem.invalidState, "Cannot read a null flat field"))
        }
        return action()
    }
}
