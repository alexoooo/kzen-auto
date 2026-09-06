package tech.kzen.auto.common.data.schema

import tech.kzen.lib.common.exec.data.shape.SchemaDiagnostic
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.lib.common.exec.data.shape.ShapeStability
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.toDataContract
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName


object LegacyDataShapeBridge {
    const val missingCellValue = "<missing>"

    fun tabular(
        header: HeaderListing,
        provenance: ShapeProvenance = ShapeProvenance.ProviderReported,
        stability: ShapeStability = ShapeStability.Stable,
        diagnostics: List<SchemaDiagnostic> = emptyList()
    ): DataShape =
        DataShape(
            DataContract(DataType.Record(header.values.map { label ->
                DataField(
                    FieldId(label.text, label.occurrence),
                    DataType.Scalar(ScalarKind.Text))
            })),
            provenance,
            stability,
            diagnostics)

    fun payload(
        type: TypeMetadata,
        provenance: ShapeProvenance = ShapeProvenance.Declared,
        stability: ShapeStability = ShapeStability.Stable,
        diagnostics: List<SchemaDiagnostic> = emptyList()
    ): DataShape =
        DataShape(type.toDataContract(), provenance, stability, diagnostics)

    fun headerOrNull(shape: DataShape): HeaderListing? {
        val record = shape.itemType.structural as? DataType.Record ?: return null
        return HeaderListing(record.fields.map { field ->
            HeaderLabel(field.id.name, field.id.occurrence)
        })
    }

    fun legacyPayloadType(shape: DataShape): TypeMetadata {
        shape.itemType.nativeByPath[DataTypePath.root]?.let { return it }
        return shape.itemType.structural.toLegacyTypeMetadata()
    }

    fun runtimeUnknown(): DataShape =
        DataShape(
            DataContract(DataType.Dynamic()),
            ShapeProvenance.RuntimeOnly,
            ShapeStability.Stable,
            emptyList())
}


private fun DataType.toLegacyTypeMetadata(): TypeMetadata =
    when (this) {
        is DataType.Scalar -> TypeMetadata(
            ClassName(kind.legacyClassName()),
            emptyList(),
            nullable)
        is DataType.Listing -> TypeMetadata(
            ClassName("kotlin.collections.List"),
            listOf(element.toLegacyTypeMetadata()),
            nullable)
        is DataType.Mapping -> TypeMetadata(
            ClassName("kotlin.collections.Map"),
            listOf(key.toLegacyTypeMetadata(), value.toLegacyTypeMetadata()),
            nullable)
        is DataType.Dynamic -> TypeMetadata(
            ClassName("kotlin.Any"),
            emptyList(),
            nullable)
        is DataType.Opaque,
        is DataType.Record,
        is DataType.Reference,
        is DataType.Union -> TypeMetadata(
            ClassName("kotlin.Any"),
            emptyList(),
            nullable)
    }


private fun ScalarKind.legacyClassName(): String =
    when (this) {
        ScalarKind.Boolean -> "kotlin.Boolean"
        is ScalarKind.Integer -> when {
            bits == 8 && signed -> "kotlin.Byte"
            bits == 16 && signed -> "kotlin.Short"
            bits == 32 && signed -> "kotlin.Int"
            bits == 64 && signed -> "kotlin.Long"
            else -> "java.math.BigInteger"
        }
        ScalarKind.Decimal -> "java.math.BigDecimal"
        is ScalarKind.Floating -> if (bits == 32) "kotlin.Float" else "kotlin.Double"
        ScalarKind.Text -> "kotlin.String"
        ScalarKind.Binary -> "kotlin.ByteArray"
        ScalarKind.Date -> "java.time.LocalDate"
        ScalarKind.Time -> "java.time.LocalTime"
        ScalarKind.Instant -> "java.time.Instant"
        ScalarKind.Duration -> "java.time.Duration"
        ScalarKind.Uuid -> "java.util.UUID"
    }
