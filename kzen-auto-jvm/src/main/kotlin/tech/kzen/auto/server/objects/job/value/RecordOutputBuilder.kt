package tech.kzen.auto.server.objects.job.value

import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.auto.server.objects.report.exec.calc.ColumnValue
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueAccess
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import java.util.concurrent.ConcurrentHashMap


/** Internal mutation surface. The returned value is published only by [finish]. */
internal class RecordOutputBuilder private constructor(
    claim: JobValueClaim,
    descriptor: ColumnProjectionDescriptor?
) {
    companion object {
        fun open(
            claim: JobValueClaim,
            descriptor: ColumnProjectionDescriptor? = null
        ): RecordOutputBuilder = RecordOutputBuilder(claim, descriptor)
    }

    private val source = claim.value
    private val record: FlatFileRecord
    private var states: MutableList<DataState>?
    private val fields: MutableList<DataField>
    private val nativeRoot: Any?
    private val nativeMetadata = source.contract.nativeByPath[DataTypePath.root]
    private var finished = false

    var projectionCount: Int = 0
        private set
    var appendCount: Int = 0
        private set

    init {
        nativeRoot = nativeMetadata?.let { source.access.native(source.root) }

        val projectedAccess = source.access as? ProjectedRecordValueAccess
        val flatAccess = source.access as? FlatFileRecord
        val projection =
            if (projectedAccess == null && flatAccess == null) {
                (descriptor ?: ColumnProjectionDescriptor.from(source.contract)).bind(source)
            }
            else {
                null
            }
        fields = initialFields(source.contract, projection).toMutableList()
        when {
            claim.exclusive && projectedAccess != null -> {
                record = projectedAccess.record
                states = projectedAccess.states?.toMutableList()
            }

            claim.exclusive && flatAccess != null -> {
                record = flatAccess
                states = null
            }

            projectedAccess != null -> {
                record = FlatFileRecord().also { it.copy(projectedAccess.record) }
                states = projectedAccess.states?.toMutableList()
            }

            flatAccess != null -> {
                record = FlatFileRecord().also { it.copy(flatAccess) }
                states = null
            }

            else -> {
                record = FlatFileRecord()
                val projectedStates = mutableListOf<DataState>()
                projectionCount = 1
                for (index in 0 until projection!!.size) {
                    val state = projection.state(index)
                    record.add(if (state == DataState.Present) scalarText(projection.scalar(index)) else "")
                    projectedStates += state
                }
                states = projectedStates.takeUnless { values ->
                    values.all { it == DataState.Present }
                }?.toMutableList()
            }
        }

        check(record.fieldCount() == fields.size) {
            "Projected record field count ${record.fieldCount()} does not match ${fields.size} fields"
        }
    }

    fun append(
        field: FieldId,
        type: DataType,
        state: DataState,
        value: ScalarExecutionValue? = null
    ) {
        checkMutable()
        require(fields.none { it.id == field }) { "Output field '$field' collides with an existing field" }
        require(state != DataState.Present || value != null) { "Present field '$field' requires a scalar value" }
        require(state == DataState.Present || value == null) { "Non-present field '$field' must not carry a value" }
        require(type is DataType.Scalar) { "Record output v1 can append scalar fields only: $field is $type" }

        record.add(value?.let(::scalarText) ?: "")
        if (states == null && state != DataState.Present) {
            states = MutableList(fields.size) { DataState.Present }
        }
        states?.add(state)
        fields += DataField(field, type, state == DataState.Absent)
        appendCount++
    }

    fun appendFrom(
        source: ColumnProjection,
        index: Int,
        rename: FieldId? = null
    ) {
        val target = rename ?: source.field(index)
        val state = source.state(index)
        append(
            target,
            source.contract(index).structural,
            state,
            if (state == DataState.Present) source.scalar(index) else null)
    }

    fun finish(): DataValue {
        checkMutable()
        finished = true

        val schema = RecordOutputSchemas.of(fields, nativeMetadata)
        val header = schema.header
        record.attachHeader(header)

        val direct = states == null &&
                (nativeRoot == null || nativeRoot === record)
        val access: ValueAccess =
            if (direct) record
            else ProjectedRecordValueAccess(record, header, states?.toList(), nativeRoot)
        return DataValue(access, DataNode(0))
    }

    private fun checkMutable() {
        check(!finished) { "Record output builder is frozen after finish()" }
    }

    private fun initialFields(
        contract: DataContract,
        projection: ColumnProjection?
    ): List<DataField> =
        when (val structural = contract.structural) {
            is DataType.Record -> structural.fields
            is DataType.Scalar -> listOf(DataField(FieldId("value"), structural))
            is DataType.Mapping -> requireNotNull(projection).descriptor.columns.map {
                DataField(it.field, it.contract.structural, optional = true)
            }
            else -> error("Unsupported builder source $structural")
        }

    private fun scalarText(value: ScalarExecutionValue): String =
        when (value) {
            is TextExecutionValue -> value.value
            is BooleanExecutionValue -> value.value.toString()
            is LongExecutionValue -> value.value.toString()
            is NumberExecutionValue -> ColumnValue.ofNumber(value.value).text
            else -> throw DataException(DataProblem(
                DataProblem.invalidOperation,
                "Binary scalar values cannot be materialized as Job columns"))
        }
}


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


private object RecordOutputSchemas {
    private data class Key(
        val record: DataType.Record,
        val native: TypeMetadata?
    )

    data class Schema(
        val contract: DataContract,
        val header: FlatRecordHeader
    )

    private val cache = ConcurrentHashMap<Key, Schema>()

    fun of(fields: List<DataField>, native: TypeMetadata?): Schema {
        val key = Key(DataType.Record(fields), native)
        return cache.computeIfAbsent(key) {
            val nativeByPath = native?.let { mapOf(DataTypePath.root to it) } ?: emptyMap()
            val contract = DataContract(it.record, nativeByPath)
            Schema(contract, FlatRecordHeader(contract))
        }
    }
}
