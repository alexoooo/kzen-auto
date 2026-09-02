package tech.kzen.auto.common.data.schema

import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldListSpec
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldSpec
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName


/** Authoring-time projection of an observed record contract into an editable schema declaration. */
object AuthoredRecordSchemaDraft {
    fun from(contract: DataContract): DataSchemaFieldListSpec? {
        val record = contract.structural as? DataType.Record
            ?: return null
        val fields = linkedMapOf<String, DataSchemaFieldSpec>()
        for (field in record.fields) {
            if (field.id.occurrence != 0 || field.id.name in fields) {
                return null
            }
            val path = DataTypePath(listOf(DataPathSegment.Field(field.id)))
            val native = contract.nativeByPath[path]
            val metadata = native?.withNullability(field.optional || field.type.nullable)
                ?: scalarMetadata(field.type, field.optional)
                ?: return null
            fields[field.id.name] = DataSchemaFieldSpec(metadata)
        }
        return DataSchemaFieldListSpec(fields)
    }


    private fun scalarMetadata(type: DataType, optional: Boolean): TypeMetadata? {
        val scalar = type as? DataType.Scalar
            ?: return null
        val base = when (val kind = scalar.kind) {
            ScalarKind.Boolean -> TypeMetadata.boolean
            is ScalarKind.Integer -> if ((kind.bits ?: 64) <= 32) {
                TypeMetadata.int
            }
            else {
                TypeMetadata.long
            }
            ScalarKind.Decimal -> TypeMetadata.of(ClassName("java.math.BigDecimal"))
            is ScalarKind.Floating -> TypeMetadata.double
            ScalarKind.Text -> TypeMetadata.string
            else -> return null
        }
        return base.withNullability(optional || scalar.nullable)
    }


    private fun TypeMetadata.withNullability(nullable: Boolean): TypeMetadata =
        TypeMetadata(className, generics, nullable)
}
