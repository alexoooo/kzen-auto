package tech.kzen.auto.server.objects.job.worker.content

import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.value.recycle.Recyclable
import tech.kzen.auto.server.objects.job.value.recycle.RecyclableAccess
import tech.kzen.auto.server.objects.job.value.recycle.RecyclablePool
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.VariantId
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import kotlin.reflect.typeOf


/**
 * The pool slot of one [Bytes]: the value's access, so [Recyclable.of] finds it, and its [bytes] as the native the
 * value hands out by identity. The slot is separate from the [Bytes] it owns because a [Recyclable]'s public members
 * would otherwise make the native describe as a record of them.
 *
 * Holds one value per slot, made again only when the metadata object changes, so a producer that reuses its
 * metadata emits without allocating.
 */
class PooledBytes(
    pool: RecyclablePool<PooledBytes>
):
    Recyclable(pool),
    RecyclableAccess
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        /** The payload's contract: a [Bytes], opaque. */
        val contract: DataContract by lazy { JobDataValues.describe(typeOf<Bytes>()) }

        private val root = DataNode(0)
    }


    //-----------------------------------------------------------------------------------------------------------------
    val bytes = Bytes()

    private var value = DataValue(this, root)

    override val recyclable: Recyclable
        get() = this


    /** The slot's value with [metadata]; the slot's own until it is sent. */
    fun value(metadata: ValueMetadata?): DataValue {
        val current = value
        if (current.metadata === metadata) {
            return current
        }
        val next = DataValue(this, root, metadata)
        value = next
        return next
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun contract(node: DataNode): DataContract {
        requireRoot(node)
        return contract
    }

    override fun state(node: DataNode): DataState {
        requireRoot(node)
        return DataState.Present
    }

    override fun native(node: DataNode): Any {
        requireRoot(node)
        return bytes
    }

    override fun activeVariant(node: DataNode): VariantId = unsupported()
    override fun selected(node: DataNode): DataNode = unsupported()
    override fun field(node: DataNode, field: FieldId): DataNode = unsupported()
    override fun entry(node: DataNode, key: ScalarExecutionValue): DataNode = unsupported()
    override fun element(node: DataNode, index: Int): DataNode = unsupported()
    override fun size(node: DataNode): Int = unsupported()
    override fun keyAt(node: DataNode, index: Int): ScalarExecutionValue = unsupported()
    override fun scalar(node: DataNode): ScalarExecutionValue = unsupported()
    override fun readBoolean(node: DataNode): Boolean = unsupported()
    override fun readLong(node: DataNode): Long = unsupported()
    override fun readDouble(node: DataNode): Double = unsupported()
    override fun readText(node: DataNode): String = unsupported()
    override fun readBinary(node: DataNode): ByteArray = unsupported()


    private fun requireRoot(node: DataNode) {
        if (node != root) {
            unsupported()
        }
    }


    private fun unsupported(): Nothing =
        throw DataAccessException(DataProblem(DataProblem.invalidOperation, "Bytes are read through their native"))
}
