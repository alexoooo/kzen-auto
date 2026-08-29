package tech.kzen.auto.server.objects.job.value

import tech.kzen.auto.common.data.schema.HeaderLabel
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.server.objects.report.exec.calc.ColumnValue
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypeAlgebra
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.TypeAcceptance
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import java.util.concurrent.ConcurrentHashMap


/** Static, value-independent description of the columns a contract exposes. */
class ColumnProjectionDescriptor private constructor(
    val source: DataContract,
    val columns: List<ColumnDescriptor>
) {
    companion object {
        private val valueField = FieldId("value")
        private val defaultCache = ConcurrentHashMap<DataContract, ColumnProjectionDescriptor>()

        fun from(
            contract: DataContract,
            mappingKeys: MappingKeyProjection? = null
        ): ColumnProjectionDescriptor =
            if (mappingKeys == null) {
                defaultCache.computeIfAbsent(contract) { create(it, null) }
            }
            else {
                create(contract, mappingKeys)
            }

        private fun create(
            contract: DataContract,
            mappingKeys: MappingKeyProjection?
        ): ColumnProjectionDescriptor {
            val columns = when (val type = contract.structural) {
                is DataType.Record -> type.fields.map { field ->
                    ColumnDescriptor(
                        field.id,
                        HeaderLabel(field.id.name, field.id.occurrence),
                        contract.child(DataPathSegment.Field(field.id)),
                        ColumnAddress.RecordPath(listOf(field.id)))
                }

                is DataType.Scalar -> listOf(ColumnDescriptor(
                    valueField,
                    HeaderLabel(valueField.name, valueField.occurrence),
                    contract,
                    ColumnAddress.Root))

                is DataType.Mapping -> {
                    val declared = mappingKeys ?: unsupported(
                        "Mapping projection requires an explicit key policy")
                    declared.entries.map { entry ->
                        ColumnDescriptor(
                            entry.field,
                            HeaderLabel(entry.field.name, entry.field.occurrence),
                            contract.child(DataPathSegment.MappingValue),
                            ColumnAddress.MappingEntry(entry.key))
                    }
                }

                is DataType.Dynamic -> unsupported("Dynamic values have no static column projection")
                is DataType.Listing -> unsupported("Listings have no column projection")
                is DataType.Opaque -> unsupported(
                    "Opaque values require a structural adapter before column projection")
                is DataType.Union -> unsupported(
                    "Union values require an explicitly selected structural variant before column projection")
            }
            return ColumnProjectionDescriptor(contract, columns)
        }

        fun select(
            contract: DataContract,
            selections: List<SelectedRecordColumn>
        ): ColumnProjectionDescriptor {
            require(contract.structural is DataType.Record) {
                "Explicit nested column selection requires a record contract"
            }
            require(selections.map { it.field }.distinct().size == selections.size) {
                "Selected output fields must be unique"
            }
            val columns = selections.map { selection ->
                require(selection.path.isNotEmpty()) { "Selected record path must not be empty" }
                var child = contract
                for (segment in selection.path) {
                    child = child.child(DataPathSegment.Field(segment))
                }
                ColumnDescriptor(
                    selection.field,
                    HeaderLabel(selection.field.name, selection.field.occurrence),
                    child,
                    ColumnAddress.RecordPath(selection.path))
            }
            return ColumnProjectionDescriptor(contract, columns)
        }

        private fun unsupported(message: String): Nothing =
            throw DataException(DataProblem(DataProblem.invalidOperation, message))
    }

    val header: HeaderListing = HeaderListing(columns.map { it.label })

    fun bind(value: DataValue): ColumnProjection {
        if (source.structural != value.contract.structural) {
            val acceptance = DataTypeAlgebra.isAssignable(source.structural, value.contract.structural)
            if (acceptance is TypeAcceptance.Rejected) {
                throw DataException(acceptance.problem)
            }
        }
        return ColumnProjection(this, value)
    }
}


class ColumnDescriptor internal constructor(
    val field: FieldId,
    val label: HeaderLabel,
    val contract: DataContract,
    internal val address: ColumnAddress
)


data class MappingKeyProjection(
    val entries: List<MappingColumn>
) {
    init {
        require(entries.map { it.field }.distinct().size == entries.size) {
            "Mapping projection fields must be unique"
        }
    }
}


data class MappingColumn(
    val field: FieldId,
    val key: ScalarExecutionValue
)


data class SelectedRecordColumn(
    val field: FieldId,
    val path: List<FieldId>
)


internal sealed interface ColumnAddress {
    data object Root: ColumnAddress
    data class RecordPath(val path: List<FieldId>): ColumnAddress
    data class MappingEntry(val key: ScalarExecutionValue): ColumnAddress
}


/** A descriptor bound to one live value; child nodes remain backing-owned inline tokens. */
class ColumnProjection internal constructor(
    val descriptor: ColumnProjectionDescriptor,
    val value: DataValue
) {
    companion object {
        const val missingText = "<missing>"
    }

    val header: HeaderListing
        get() = descriptor.header

    val size: Int
        get() = descriptor.columns.size

    fun field(index: Int): FieldId = descriptor.columns[index].field

    fun contract(index: Int): DataContract = descriptor.columns[index].contract

    fun state(index: Int): DataState =
        try {
            value.access.state(node(index))
        }
        catch (e: DataAccessException) {
            if (descriptor.columns[index].address is ColumnAddress.MappingEntry) {
                DataState.Absent
            }
            else {
                throw e
            }
        }

    fun node(index: Int): DataNode =
        when (val address = descriptor.columns[index].address) {
            ColumnAddress.Root -> value.root
            is ColumnAddress.RecordPath -> address.path.fold(value.root) { node, field ->
                value.access.field(node, field)
            }
            is ColumnAddress.MappingEntry -> value.access.entry(value.root, address.key)
        }

    fun scalar(index: Int): ScalarExecutionValue = value.access.scalar(node(index))
    fun readBoolean(index: Int): Boolean = value.access.readBoolean(node(index))
    fun readLong(index: Int): Long = value.access.readLong(node(index))
    fun readDouble(index: Int): Double = value.access.readDouble(node(index))
    fun readText(index: Int): String = value.access.readText(node(index))

    fun columnValue(index: Int): ColumnValue =
        when (state(index)) {
            DataState.Absent -> ColumnValue.ofText(missingText)
            DataState.Null -> ColumnValue.ofScalar(null)
            DataState.Present -> when (val scalar = scalar(index)) {
                is TextExecutionValue -> ColumnValue.ofText(scalar.value)
                is BooleanExecutionValue -> ColumnValue.ofScalar(scalar.value)
                is LongExecutionValue -> ColumnValue.ofScalar(scalar.value)
                is NumberExecutionValue -> ColumnValue.ofNumber(scalar.value)
                else -> ColumnValue.ofText(scalar.toString())
            }
        }

    fun render(index: Int): String = columnValue(index).text
}
