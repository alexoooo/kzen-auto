package tech.kzen.auto.server.objects.job.worker.format

import tech.kzen.auto.server.objects.job.worker.content.FileValues
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.VariantId
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.ValueAccess
import tech.kzen.lib.common.exec.data.value.ValueMetadata


/**
 * A chunk's metadata record `{name, parent}`, read as `DataOverlay.record(null, fields)` reads it: the same contract,
 * fields and failures. Its contract is the [Shape]'s, made once per parent contract, so a chunk whose parent
 * metadata is a new object (each record after a Formula) costs this access and its [ValueMetadata], not a contract.
 *
 * Field `n` is node `n + 1`; nodes inside a field are numbered from there as they are first reached.
 */
internal class ChunkMetadata private constructor(
    private val shape: Shape,
    private val name: DataValue,
    private val parent: DataValue?
):
    ValueAccess
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val text = DataContract(DataType.Scalar(ScalarKind.Text))
        private val nameField = FieldId(FileValues.name)
        private val parentField = FieldId(FileValues.parent)
        private val root = DataNode(0)


        /** The metadata record's contract: `name`, then `parent` when [parent] (the parent metadata's) is known. */
        fun contract(parent: DataContract?): DataContract {
            val fields = ArrayList<Pair<FieldId, DataContract>>(2)
            fields += nameField to text
            if (parent != null) {
                fields += parentField to parent
            }
            return DataOverlay.contract(null, fields)
        }


        /** The name field's value, made once per name. */
        fun name(name: String): DataValue =
            LiteralDataValues.lift(name, text)
    }


    /** The contract of chunk metadata whose parent metadata has contract [parent]. */
    class Shape(
        val parent: DataContract?
    ) {
        val contract = ChunkMetadata.contract(parent)

        /** Metadata named [name] (from [ChunkMetadata.name]) over [parent], whose contract is this shape's. */
        fun metadata(name: DataValue, parent: ValueMetadata?): ValueMetadata =
            ValueMetadata(ChunkMetadata(this, name, parent?.value), root)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private class Bound(val access: ValueAccess, val node: DataNode)

    // Nodes inside a field, made only when something reads that deep (a path template, a preview)
    private var nested: MutableList<Bound>? = null
    private var tokens: MutableMap<ValueAccess, MutableMap<DataNode, DataNode>>? = null


    private val fieldCount: Int
        get() = if (parent == null) 1 else 2


    private fun fieldValue(index: Int): DataValue =
        if (index == 0) name else checkNotNull(parent)


    private fun intern(access: ValueAccess, node: DataNode): DataNode = synchronized(this) {
        val nodes = nested ?: mutableListOf<Bound>().also { nested = it }
        val byAccess = tokens ?: HashMap<ValueAccess, MutableMap<DataNode, DataNode>>().also { tokens = it }
        byAccess.getOrPut(access) { mutableMapOf() }.getOrPut(node) {
            nodes += Bound(access, node)
            DataNode((fieldCount + nodes.size).toLong())
        }
    }


    private inline fun <T> read(node: DataNode, action: (ValueAccess, DataNode) -> T): T {
        val token = node.token
        if (token in 1..fieldCount) {
            val value = fieldValue(token.toInt() - 1)
            return action(value.access, value.root)
        }
        val bound = synchronized(this) {
            val nodes = nested
            val index = token - fieldCount - 1
            if (nodes == null || index < 0 || index >= nodes.size) {
                throw DataAccessException(DataProblem(
                    DataProblem.invalidOperation, "Operation requires a composed field node"))
            }
            nodes[index.toInt()]
        }
        return action(bound.access, bound.node)
    }


    private inline fun navigate(node: DataNode, action: (ValueAccess, DataNode) -> DataNode): DataNode =
        read(node) { access, original -> intern(access, action(access, original)) }


    private fun rootOnly(operation: String): Nothing =
        throw DataAccessException(DataProblem(
            DataProblem.invalidOperation, "A composed record does not support $operation"))


    //-----------------------------------------------------------------------------------------------------------------
    override fun contract(node: DataNode): DataContract =
        if (node == root) shape.contract else read(node) { a, n -> a.contract(n) }

    override fun state(node: DataNode): DataState =
        if (node == root) DataState.Present else read(node) { a, n -> a.state(n) }

    override fun field(node: DataNode, field: FieldId): DataNode =
        if (node == root) {
            when {
                field == nameField -> DataNode(1)
                field == parentField && parent != null -> DataNode(2)
                else -> throw DataAccessException(DataProblem(DataProblem.invalidPath, "Unknown field '$field'"))
            }
        }
        else {
            navigate(node) { a, n -> a.field(n, field) }
        }

    override fun native(node: DataNode): Any =
        if (node == root) rootOnly("native access") else read(node) { a, n -> a.native(n) }

    override fun size(node: DataNode): Int =
        if (node == root) fieldCount else read(node) { a, n -> a.size(n) }

    override fun activeVariant(node: DataNode): VariantId =
        if (node == root) rootOnly("variants") else read(node) { a, n -> a.activeVariant(n) }
    override fun selected(node: DataNode): DataNode =
        if (node == root) rootOnly("variants") else navigate(node) { a, n -> a.selected(n) }
    override fun entry(node: DataNode, key: ScalarExecutionValue): DataNode =
        if (node == root) rootOnly("keyed entries") else navigate(node) { a, n -> a.entry(n, key) }
    override fun element(node: DataNode, index: Int): DataNode =
        if (node == root) rootOnly("elements") else navigate(node) { a, n -> a.element(n, index) }
    override fun keyAt(node: DataNode, index: Int): ScalarExecutionValue =
        if (node == root) rootOnly("keys") else read(node) { a, n -> a.keyAt(n, index) }
    override fun scalar(node: DataNode): ScalarExecutionValue =
        if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.scalar(n) }
    override fun readBoolean(node: DataNode): Boolean =
        if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.readBoolean(n) }
    override fun readLong(node: DataNode): Long =
        if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.readLong(n) }
    override fun readDouble(node: DataNode): Double =
        if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.readDouble(n) }
    override fun readText(node: DataNode): String =
        if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.readText(n) }
    override fun readBinary(node: DataNode): ByteArray =
        if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.readBinary(n) }
}
