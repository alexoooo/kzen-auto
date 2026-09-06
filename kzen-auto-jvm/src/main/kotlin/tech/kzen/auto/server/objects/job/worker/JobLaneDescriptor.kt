package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.server.objects.job.value.ColumnProjectionDescriptor
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ResolvedDataContract
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.toDataContract
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.platform.ClassName


/** The single static authority for a Job lane. */
class JobLaneDescriptor(
    val contract: DataContract,
    val resolved: ResolvedDataContract? = null
) {
    constructor(
        payloadType: TypeMetadata?,
        flatColumns: HeaderListing?
    ): this(fromLegacy(payloadType, flatColumns).contract)

    companion object {
        val unknown = JobLaneDescriptor(DataContract(DataType.Dynamic()))

        fun fromLegacy(
            payloadType: TypeMetadata?,
            flatColumns: HeaderListing?
        ): JobLaneDescriptor {
            if (flatColumns == null || flatColumns.values.isEmpty()) {
                return payloadType?.let { JobLaneDescriptor(it.toDataContract()) } ?: unknown
            }

            val record = DataType.Record(
                flatColumns.values.map { label ->
                    DataField(
                        FieldId(label.text, label.occurrence),
                        DataType.Scalar(ScalarKind.Text))
                },
                nullable = payloadType?.nullable ?: false)
            val native = payloadType?.let { mapOf(DataTypePath.root to it) } ?: emptyMap()
            return JobLaneDescriptor(DataContract(record, native))
        }
    }

    init {
        require(resolved == null || resolved.contract == contract) {
            "Resolved Job lane must describe the canonical contract"
        }
    }

    val structuralKey: Digest = contract.structuralDigest
    val declarationKey: Digest = contract.declarationDigest
    val resolvedKey: Int? = resolved?.hashCode()

    val payloadType: TypeMetadata?
        get() = legacyPayloadType()

    val flatColumns: HeaderListing?
        get() = legacyFlatColumns()

    fun legacyPayloadType(): TypeMetadata? =
        contract.nativeByPath[DataTypePath.root] ?: scalarMetadata(contract.structural)

    fun legacyFlatColumns(): HeaderListing? =
        when (contract.structural) {
            is DataType.Record -> ColumnProjectionDescriptor.from(contract).header
            is DataType.Scalar,
            is DataType.Opaque,
            is DataType.Reference,
            is DataType.Listing -> HeaderListing.empty
            is DataType.Mapping,
            is DataType.Dynamic,
            is DataType.Union -> null
        }

    fun consumerColumns(): HeaderListing? =
        try {
            ColumnProjectionDescriptor.from(contract).header
        }
        catch (_: RuntimeException) {
            null
        }

    fun consumerFlatColumns(): HeaderListing? = consumerColumns()

    fun boundaryType(): TypeMetadata? {
        payloadType?.let { return it }
        val columns = flatColumns ?: return null
        if (columns.values.isEmpty()) return null
        return TypeMetadata(
            ClassName("kotlin.collections.Map"),
            listOf(TypeMetadata.string, TypeMetadata.string),
            false)
    }
    private fun scalarMetadata(type: DataType): TypeMetadata? {
        val scalar = type as? DataType.Scalar ?: return null
        val name = when (val kind = scalar.kind) {
            ScalarKind.Boolean -> "kotlin.Boolean"
            is ScalarKind.Integer -> when {
                kind.signed && kind.bits == 8 -> "kotlin.Byte"
                kind.signed && kind.bits == 16 -> "kotlin.Short"
                kind.signed && kind.bits == 32 -> "kotlin.Int"
                kind.signed && kind.bits == 64 -> "kotlin.Long"
                !kind.signed && kind.bits == 8 -> "kotlin.UByte"
                !kind.signed && kind.bits == 16 -> "kotlin.UShort"
                !kind.signed && kind.bits == 32 -> "kotlin.UInt"
                !kind.signed && kind.bits == 64 -> "kotlin.ULong"
                else -> "java.math.BigInteger"
            }
            ScalarKind.Decimal -> "java.math.BigDecimal"
            is ScalarKind.Floating -> if (kind.bits == 32) "kotlin.Float" else "kotlin.Double"
            ScalarKind.Text -> "kotlin.String"
            ScalarKind.Binary -> "kotlin.ByteArray"
            ScalarKind.Date -> "java.time.LocalDate"
            ScalarKind.Time -> "java.time.LocalTime"
            ScalarKind.Instant -> "java.time.Instant"
            ScalarKind.Duration -> "java.time.Duration"
            ScalarKind.Uuid -> "java.util.UUID"
        }
        return TypeMetadata(ClassName(name), emptyList(), scalar.nullable)
    }
}


class JobLaneAttempt(
    val lane: JobLaneDescriptor,
    val errorMessage: String?
)


class JobLaneContext(
    val parameters: BindingSchema,
    val graphStructure: GraphStructure,
    val classLoader: ClassLoader
)
