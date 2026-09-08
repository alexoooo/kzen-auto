package tech.kzen.auto.server.objects.job.value

import tech.kzen.lib.common.exec.*
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.*
import tech.kzen.lib.common.exec.data.value.*
import java.util.IdentityHashMap


/** Contract composition shared by Formula validation and its live record view. */
internal object RecordOverlay {
    fun fields(contract: DataContract): List<DataField> = when (val type = contract.structural) {
        is DataType.Record -> type.fields
        is DataType.Scalar -> listOf(DataField(FieldId("value"), type))
        else -> error("Record overlay requires a record or scalar, found $type")
    }

    fun appendContract(source: DataContract, added: List<DataField>): DataContract {
        val base = recordContract(source)
        return compose(base, added.map { it to DataContract(it.type) })
    }

    fun carryFields(source: DataContract, selection: CarrySelection): List<Pair<DataField, FieldId>> {
        val fields = fields(source)
        val renames = when (selection) {
            CarrySelection.None -> return emptyList()
            is CarrySelection.All -> selection.renames
            is CarrySelection.Selected -> {
                val selected = selection.fields.associate { it.source to it.rename }
                require(selected.size == selection.fields.size) { "Carry fields must be unique" }
                val unknown = selected.keys - fields.map { it.id }.toSet()
                require(unknown.isEmpty()) { "Unknown carry fields: $unknown" }
                return fields.filter { it.id in selected }.map { it to (selected[it.id] ?: it.id) }
            }
        }
        return fields.map { it to (renames[it.id] ?: it.id) }
    }

    fun carryContract(target: DataContract, source: DataContract, selection: CarrySelection): DataContract {
        val sourceRecord = recordContract(source)
        return compose(recordContract(target), carryFields(source, selection).map { (field, renamed) ->
            field.copy(id = renamed) to sourceRecord.child(DataPathSegment.Field(field.id))
        })
    }

    private fun recordContract(source: DataContract): DataContract {
        if (source.structural is DataType.Record) return source
        val prefix = DataPathSegment.Field(FieldId("value"))
        val natives = source.nativeByPath.mapKeys { (path, _) -> DataTypePath(listOf(prefix) + path.segments) }
        return DataContract(DataType.Record(fields(source)), natives, source.definitions, source.definitionNatives)
    }

    private fun compose(base: DataContract, additions: List<Pair<DataField, DataContract>>): DataContract {
        val fields = fields(base).toMutableList()
        val natives = base.nativeByPath.toMutableMap()
        val definitions = base.definitions.toMutableMap()
        val definitionNatives = base.definitionNatives.toMutableMap()
        for ((field, contract) in additions) {
            require(fields.none { it.id == field.id }) { "Output field '${field.id}' collides with an existing field" }
            fields += field
            val prefix = DataPathSegment.Field(field.id)
            contract.nativeByPath.forEach { (path, metadata) ->
                natives[DataTypePath(listOf(prefix) + path.segments)] = metadata
            }
            contract.definitions.forEach { (id, type) ->
                require(id !in definitions || definitions[id] == type) { "Conflicting carried definition '$id'" }
                definitions[id] = type
            }
            contract.definitionNatives.forEach { (id, metadata) ->
                require(id !in definitionNatives || definitionNatives[id] == metadata) {
                    "Conflicting carried native definition '$id'"
                }
                definitionNatives[id] = metadata
            }
        }
        return DataContract(DataType.Record(fields, base.structural.nullable), natives, definitions, definitionNatives)
    }

    fun append(source: DataValue, calculated: List<CalculatedFieldValue>): DataValue {
        if (calculated.isEmpty()) return source
        val contract = appendContract(source.contract, calculated.map { DataField(it.field, it.type) })
        val bindings = bindings(source).toMutableMap()
        for (field in calculated) {
            val raw = when (val value = field.value) {
                null -> null
                is BooleanExecutionValue -> value.value
                is LongExecutionValue -> value.value
                is NumberExecutionValue -> value.value
                is TextExecutionValue -> value.value
                is BinaryExecutionValue -> value.value
                else -> error("Unsupported calculated scalar $value")
            }
            val literal = LiteralDataValues.lift(raw, DataContract(field.type))
            bindings[field.field] = lazy { OverlayNode(literal.access, literal.root) }
        }
        return view(source, contract, bindings)
    }

    fun carry(target: DataValue, source: DataValue, selection: CarrySelection): DataValue {
        val contract = carryContract(target.contract, source.contract, selection)
        val bindings = bindings(target).toMutableMap()
        val sourceBindings = bindings(source)
        for ((field, rename) in carryFields(source.contract, selection)) {
            bindings[rename] = sourceBindings.getValue(field.id)
        }
        return view(target, contract, bindings)
    }

    private fun bindings(source: DataValue): Map<FieldId, Lazy<OverlayNode>> {
        val overlay = source.access as? OverlayAccess
        if (overlay != null && source.root == DataNode(0)) return overlay.bindings
        return fields(source.contract).associate { field ->
            field.id to lazy {
                if (source.contract.structural is DataType.Scalar) OverlayNode(source.access, source.root)
                else OverlayNode(source.access, source.access.field(source.root, field.id))
            }
        }
    }

    private fun view(source: DataValue, contract: DataContract, bindings: Map<FieldId, Lazy<OverlayNode>>): DataValue {
        val access = source.access as? OverlayAccess
        val nativeSource = if (access != null && source.root == DataNode(0)) access.nativeSource else source
        return DataValue(OverlayAccess(contract, bindings, nativeSource), DataNode(0))
    }
}


/** Tokens belong to this view; delegated tokens are interned so cycles remain navigable without copying. */
private data class OverlayNode(val access: ValueAccess, val root: DataNode)


private class OverlayAccess(
    private val outputContract: DataContract,
    val bindings: Map<FieldId, Lazy<OverlayNode>>,
    val nativeSource: DataValue
): ValueAccess {
    private val nodes = mutableListOf<OverlayNode>()
    private val tokens = IdentityHashMap<ValueAccess, MutableMap<DataNode, DataNode>>()

    @Synchronized
    private fun intern(value: OverlayNode): DataNode =
        tokens.getOrPut(value.access) { mutableMapOf() }.getOrPut(value.root) {
            nodes += value
            DataNode(nodes.size.toLong())
        }

    @Synchronized
    private fun value(node: DataNode): OverlayNode {
        if (node.token <= 0 || node.token > nodes.size) throw DataAccessException(DataProblem(
            DataProblem.invalidOperation, "Operation requires an overlay field node"))
        return nodes[node.token.toInt() - 1]
    }

    private inline fun <T> read(node: DataNode, action: (ValueAccess, DataNode) -> T): T {
        val value = value(node)
        return action(value.access, value.root)
    }

    private inline fun navigate(node: DataNode, action: (ValueAccess, DataNode) -> DataNode): DataNode =
        read(node) { access, original -> intern(OverlayNode(access, action(access, original))) }

    override fun contract(node: DataNode): DataContract =
        if (node.token == 0L) outputContract else read(node) { access, original -> access.contract(original) }
    override fun state(node: DataNode): DataState =
        if (node.token == 0L) DataState.Present else read(node) { access, original -> access.state(original) }
    override fun field(node: DataNode, field: FieldId): DataNode =
        if (node.token == 0L) intern((bindings[field] ?: throw DataAccessException(DataProblem(
            DataProblem.invalidPath, "Unknown overlay field '$field'"))).value)
        else navigate(node) { access, original -> access.field(original, field) }
    override fun native(node: DataNode): Any =
        if (node.token == 0L) nativeSource.access.native(nativeSource.root)
        else read(node) { access, original -> access.native(original) }
    override fun size(node: DataNode): Int =
        if (node.token == 0L) bindings.size else read(node) { access, original -> access.size(original) }
    override fun activeVariant(node: DataNode): VariantId = read(node) { a, n -> a.activeVariant(n) }
    override fun selected(node: DataNode): DataNode = navigate(node) { a, n -> a.selected(n) }
    override fun entry(node: DataNode, key: ScalarExecutionValue): DataNode = navigate(node) { a, n -> a.entry(n, key) }
    override fun element(node: DataNode, index: Int): DataNode = navigate(node) { a, n -> a.element(n, index) }
    override fun keyAt(node: DataNode, index: Int): ScalarExecutionValue = read(node) { a, n -> a.keyAt(n, index) }
    override fun scalar(node: DataNode): ScalarExecutionValue = read(node) { a, n -> a.scalar(n) }
    override fun readBoolean(node: DataNode): Boolean = read(node) { a, n -> a.readBoolean(n) }
    override fun readLong(node: DataNode): Long = read(node) { a, n -> a.readLong(n) }
    override fun readDouble(node: DataNode): Double = read(node) { a, n -> a.readDouble(n) }
    override fun readText(node: DataNode): String = read(node) { a, n -> a.readText(n) }
    override fun readBinary(node: DataNode): ByteArray = read(node) { a, n -> a.readBinary(n) }
}
