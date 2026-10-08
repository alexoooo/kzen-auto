package tech.kzen.auto.server.objects.job.value.recycle

import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.VariantId
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import java.util.concurrent.atomic.AtomicInteger


/**
 * Test recyclable: a pooled record `{id, name}` that is its own access, with one cached [value]. Every read
 * compares the object's generation with the one it was filled at, so a read after the object went back to its
 * pool fails by name and is counted in [useAfterRecycle] (a reader that swallows the failure, like a preview's
 * capture, still leaves the count behind). It has no native type, so a boundary materializes it as a map.
 */
class PooledRecord(
    pool: RecyclablePool<PooledRecord>
):
    Recyclable(pool),
    RecyclableAccess
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val idField = FieldId("id")
        val nameField = FieldId("name")

        private val root = DataNode(0)
        private val idNode = DataNode(1)
        private val nameNode = DataNode(2)

        private val idContract = DataContract(DataType.Scalar(ScalarKind.Integer(64)))
        private val nameContract = DataContract(DataType.Scalar(ScalarKind.Text))
        private val contract = DataContract(DataType.Record(listOf(
            DataField(idField, idContract.structural),
            DataField(nameField, nameContract.structural))))

        val useAfterRecycle = AtomicInteger(0)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var id = 0L
    private var name = ""
    private var filledGeneration = 0

    val value = DataValue(this, root)

    override val recyclable: Recyclable
        get() = this


    fun fill(id: Long, name: String): DataValue {
        this.id = id
        this.name = name
        filledGeneration = generation
        return value
    }


    private fun checkLive() {
        if (generation != filledGeneration) {
            useAfterRecycle.incrementAndGet()
            throw IllegalStateException("PooledRecord $id was read after it was recycled")
        }
    }


    private fun unsupported(): Nothing =
        throw DataAccessException(DataProblem(DataProblem.invalidOperation, "Not supported by PooledRecord"))


    //-----------------------------------------------------------------------------------------------------------------
    override fun contract(node: DataNode): DataContract =
        when (node) {
            root -> contract
            idNode -> idContract
            nameNode -> nameContract
            else -> unsupported()
        }

    override fun state(node: DataNode): DataState {
        checkLive()
        return DataState.Present
    }

    override fun field(node: DataNode, field: FieldId): DataNode {
        checkLive()
        return when {
            node != root -> unsupported()
            field == idField -> idNode
            field == nameField -> nameNode
            else -> unsupported()
        }
    }

    override fun scalar(node: DataNode): ScalarExecutionValue =
        when (node) {
            idNode -> LongExecutionValue(readLong(node))
            nameNode -> TextExecutionValue(readText(node))
            else -> unsupported()
        }

    override fun readLong(node: DataNode): Long {
        checkLive()
        return if (node == idNode) id else unsupported()
    }

    override fun readText(node: DataNode): String {
        checkLive()
        return if (node == nameNode) name else unsupported()
    }

    override fun native(node: DataNode): Any =
        throw DataAccessException(DataProblem(DataProblem.nativeTypeMissing, "PooledRecord has no native object"))

    override fun activeVariant(node: DataNode): VariantId = unsupported()
    override fun selected(node: DataNode): DataNode = unsupported()
    override fun entry(node: DataNode, key: ScalarExecutionValue): DataNode = unsupported()
    override fun element(node: DataNode, index: Int): DataNode = unsupported()
    override fun size(node: DataNode): Int = unsupported()
    override fun keyAt(node: DataNode, index: Int): ScalarExecutionValue = unsupported()
    override fun readBoolean(node: DataNode): Boolean = unsupported()
    override fun readDouble(node: DataNode): Double = unsupported()
    override fun readBinary(node: DataNode): ByteArray = unsupported()
}
