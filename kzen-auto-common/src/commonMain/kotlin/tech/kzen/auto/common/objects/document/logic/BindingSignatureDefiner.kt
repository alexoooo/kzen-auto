package tech.kzen.auto.common.objects.document.logic

import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.toDataContract
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames


/** Shared lowering from authored Logic metadata to executable binding contracts. */
object BindingSignatureDefiner {
    fun contract(type: TypeMetadata): DataContract =
        if (type == TypeMetadata.any || type == TypeMetadata.anyNullable) {
            DataContract(DataType.Dynamic(nullable = type.nullable))
        }
        else {
            type.toDataContract()
        }

    /** Legacy expression typing at the native boundary; canonical execution retains [DataContract]. */
    fun metadata(contract: DataContract): TypeMetadata =
        contract.nativeByPath[DataTypePath.root] ?: metadata(contract.structural)

    private fun metadata(type: DataType): TypeMetadata =
        when (type) {
            is DataType.Scalar -> TypeMetadata(ClassName(when (val kind = type.kind) {
                ScalarKind.Boolean -> "kotlin.Boolean"
                is ScalarKind.Integer -> when (kind.bits) {
                    8 -> "kotlin.Byte"
                    16 -> "kotlin.Short"
                    32 -> "kotlin.Int"
                    else -> "kotlin.Long"
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
            }), emptyList(), type.nullable)
            is DataType.Listing -> TypeMetadata(
                ClassNames.kotlinList,
                listOf(metadata(type.element)),
                type.nullable)
            is DataType.Mapping -> TypeMetadata(
                ClassName("kotlin.collections.Map"),
                listOf(metadata(type.key), metadata(type.value)),
                type.nullable)
            else -> TypeMetadata(ClassNames.kotlinAny, emptyList(), type.nullable)
        }
}
