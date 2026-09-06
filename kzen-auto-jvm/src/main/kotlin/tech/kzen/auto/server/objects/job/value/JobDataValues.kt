package tech.kzen.auto.server.objects.job.value

import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.HeaderLabel
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.auto.plugin.api.data.Borrowed
import tech.kzen.auto.server.exec.job.ownership.NativeIdentityRegistry
import tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.ValueAccess
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.ScalarExecutionValue
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KType


/** Process-lifetime adapter entry point for values entering Job transport from native Logic boundaries. */
internal object JobDataValues {
    // A read through a native some run has closed fails by name (E9): the process-wide identity registry guards it
    private val adapters = DefaultDataAdapterRegistry(livenessGuard = NativeIdentityRegistry.global.guard)
    private val flatHeaders = ConcurrentHashMap<HeaderListing, FlatRecordHeader>()

    fun lift(value: Any?, expected: DataContract? = null): DataValue {
        if (value is Borrowed<*>) {
            // The wrapper is an ownership declaration, not data: the value reads as the wrapped object, and no
            // run adopts it (E9)
            NativeIdentityRegistry.global.markBorrowed(value.value)
            return adapters.lift(value.value, expected)
        }
        return adapters.lift(value, expected)
    }


    /**
     * The design-time contract of a JVM type, through the same adapter registry that lifts values at run time —
     * so an expression's inferred record, bean, enum or Set shape shows its columns before any execution and
     * matches what the run emits. Throws [tech.kzen.lib.common.exec.data.problem.DataException] for a type the
     * registry refuses (a streaming type as a value, a class with conflicting properties).
     */
    fun describe(type: KType): DataContract = adapters.describe(type)

    fun flat(header: HeaderListing, record: FlatFileRecord): DataValue {
        val flatHeader = flatHeaders.computeIfAbsent(header) {
            FlatRecordHeader(LegacyDataShapeBridge.tabular(it).itemType)
        }
        record.attachHeader(flatHeader)
        return DataValue(record, DataNode(0))
    }

    fun projectedRecord(
        contract: DataContract,
        record: FlatFileRecord,
        states: List<DataState>
    ): DataValue {
        val header = FlatRecordHeader(contract)
        record.attachHeader(header)
        val access = ProjectedRecordValueAccess(record, header, states, null)
        return DataValue(access, DataNode(0))
    }

    fun nativeRecord(
        header: HeaderListing,
        record: FlatFileRecord,
        nativeRoot: Any,
        nativeMetadata: TypeMetadata
    ): DataValue {
        val structural = LegacyDataShapeBridge.tabular(header).itemType.structural
        val contract = DataContract(structural, mapOf(DataTypePath.root to nativeMetadata))
        val flatHeader = FlatRecordHeader(contract)
        record.attachHeader(flatHeader)
        return DataValue(ProjectedRecordValueAccess(record, flatHeader, null, nativeRoot), DataNode(0))
    }

    fun projection(value: DataValue): ColumnProjection {
        val mapping = value.type as? DataType.Mapping
        if (mapping != null) {
            val entries = (0 until value.access.size(value.root)).map { index ->
                val key = value.access.keyAt(value.root, index)
                MappingColumn(FieldId(scalarText(key)), key)
            }
            return ColumnProjectionDescriptor.from(
                value.contract, MappingKeyProjection(entries)).bind(value)
        }
        return ColumnProjectionDescriptor.from(value.contract).bind(value)
    }

    private fun scalarText(value: tech.kzen.lib.common.exec.ScalarExecutionValue): String =
        when (value) {
            is TextExecutionValue -> value.value
            is BooleanExecutionValue -> value.value.toString()
            is LongExecutionValue -> value.value.toString()
            is NumberExecutionValue -> value.value.toString()
            else -> value.toString()
        }

    fun record(projection: ColumnProjection): FlatFileRecord =
        when (val access = projection.value.access) {
            is FlatFileRecord -> access
            else -> FlatFileRecord.of((0 until projection.size).map(projection::render))
        }

    fun native(value: DataValue): Any? {
        if (value.access.state(value.root) == DataState.Null) {
            return null
        }
        if (value.contract.nativeByPath[DataTypePath.root] != null) {
            return value.access.native(value.root)
        }
        if (value.access is FlatFileRecord) {
            // A flat-only lane has columns but no payload receiver, matching the pre-DataValue contract.
            return null
        }
        return when (val type = value.type) {
            is DataType.Scalar -> when (type.kind) {
                ScalarKind.Boolean -> value.access.readBoolean(value.root)
                is ScalarKind.Integer -> value.access.readLong(value.root)
                ScalarKind.Decimal -> exactDecimal(value.access.scalar(value.root))
                is ScalarKind.Floating -> value.access.readDouble(value.root)
                ScalarKind.Binary -> value.access.readBinary(value.root)
                else -> value.access.readText(value.root)
            }
            else -> boundary(value)
        }
    }

    fun boundary(value: DataValue): Any? {
        scalarNativeProjection(value)?.let { return it }
        return boundaryNode(value.access, value.root)
    }


    /**
     * Literal/default snapshots have canonical scalar backing (integers are Long), while their contract keeps
     * the declared JVM scalar type. Project that exact declared type before asking the backing for its native
     * object; ordinary native objects still take the identity-preserving path in [boundaryNode].
     */
    private fun scalarNativeProjection(value: DataValue): Any? {
        if (value.access.state(value.root) != DataState.Present || value.type !is DataType.Scalar) {
            return null
        }
        val native = value.contract.nativeByPath[DataTypePath.root] ?: return null
        return when (native.className.asString()) {
            "kotlin.Boolean", "java.lang.Boolean" -> value.access.readBoolean(value.root)
            "kotlin.Byte", "java.lang.Byte" -> value.access.readLong(value.root).toByte()
            "kotlin.Short", "java.lang.Short" -> value.access.readLong(value.root).toShort()
            "kotlin.Int", "java.lang.Integer" -> value.access.readLong(value.root).toInt()
            "kotlin.Long", "java.lang.Long" -> value.access.readLong(value.root)
            "kotlin.Float", "java.lang.Float" -> value.access.readDouble(value.root).toFloat()
            "kotlin.Double", "java.lang.Double" -> value.access.readDouble(value.root)
            "java.math.BigDecimal" -> exactDecimal(value.access.scalar(value.root))
            "kotlin.String", "java.lang.String" -> value.access.readText(value.root)
            "kotlin.ByteArray", "byte[]" -> value.access.readBinary(value.root)
            else -> null
        }
    }

    private fun boundaryNode(access: ValueAccess, node: DataNode): Any? {
        when (access.state(node)) {
            DataState.Absent -> return null
            DataState.Null -> return null
            DataState.Present -> {}
        }
        try {
            return access.native(node)
        }
        catch (_: DataAccessException) {
            // Continue with structural materialization.
        }
        return when (val type = access.contract(node).expanded().structural) {
            is DataType.Scalar -> scalarBoundary(access, node, type.kind)
            is DataType.Record -> LinkedHashMap<String, Any?>(type.fields.size).also { result ->
                for (field in type.fields) {
                    result[HeaderLabel(field.id.name, field.id.occurrence).render()] =
                        boundaryNode(access, access.field(node, field.id))
                }
            }
            is DataType.Listing -> (0 until access.size(node)).map { index ->
                boundaryNode(access, access.element(node, index))
            }
            is DataType.Mapping -> LinkedHashMap<Any?, Any?>(access.size(node)).also { result ->
                for (index in 0 until access.size(node)) {
                    val key = access.keyAt(node, index)
                    result[key.toString()] = boundaryNode(access, access.entry(node, key))
                }
            }
            is DataType.Union -> boundaryNode(access, access.selected(node))
            is DataType.Dynamic,
            is DataType.Reference,
            is DataType.Opaque -> throw DataAccessException(
                tech.kzen.lib.common.exec.data.problem.DataProblem(
                    tech.kzen.lib.common.exec.data.problem.DataProblem.nativeTypeMissing,
                    "Boundary materialization requires a concrete structural or native value"))
        }
    }


    private fun scalarBoundary(access: ValueAccess, node: DataNode, kind: ScalarKind): Any =
        when (kind) {
            ScalarKind.Boolean -> access.readBoolean(node)
            is ScalarKind.Integer -> {
                val value = access.readLong(node)
                val bits = kind.bits ?: 64
                when {
                    !kind.signed && bits <= 8 -> value.toUByte()
                    !kind.signed && bits <= 16 -> value.toUShort()
                    !kind.signed && bits <= 32 -> value.toUInt()
                    !kind.signed -> value.toULong()
                    bits <= 8 -> value.toByte()
                    bits <= 16 -> value.toShort()
                    bits <= 32 -> value.toInt()
                    else -> value
                }
            }
            ScalarKind.Decimal -> exactDecimal(access.scalar(node))
            is ScalarKind.Floating -> access.readDouble(node)
            ScalarKind.Binary -> access.readBinary(node)
            else -> access.readText(node)
        }


    private fun exactDecimal(value: ScalarExecutionValue): BigDecimal =
        when (value) {
            is TextExecutionValue -> BigDecimal(value.value)
            is LongExecutionValue -> BigDecimal.valueOf(value.value)
            is NumberExecutionValue -> BigDecimal(value.value.toString())
            else -> throw DataAccessException(
                tech.kzen.lib.common.exec.data.problem.DataProblem(
                    tech.kzen.lib.common.exec.data.problem.DataProblem.invalidOperation,
                    "Decimal scalar must use a canonical numeric execution value"))
        }
}
