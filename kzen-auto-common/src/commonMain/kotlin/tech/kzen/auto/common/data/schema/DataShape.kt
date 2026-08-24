package tech.kzen.auto.common.data.schema

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tech.kzen.auto.common.data.model.DataModelKeys
import tech.kzen.auto.common.data.model.requiredList
import tech.kzen.auto.common.data.model.requiredMap
import tech.kzen.auto.common.data.model.requiredModelMap
import tech.kzen.auto.common.data.model.requiredText
import tech.kzen.auto.common.data.model.requiredValue
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName


/**
 * Tabular structure or payload type, kept distinct for source inspection; see
 * `kzen/docs/analysis/2026-08-20_job-data-source.md` §4.
 */
@Serializable(with = DataShapeSerializer::class)
sealed interface DataShape {
    data class Tabular(
        val header: HeaderListing
    ): DataShape


    data class Payload(
        val type: TypeMetadata
    ): DataShape


    companion object {
        const val missingCellValue = "<missing>"


        fun ofExecutionValue(value: ExecutionValue): DataShape {
            val map = value.requiredModelMap("DataShape")
            return when (val kind = map.requiredText(DataModelKeys.kind)) {
                DataModelKeys.tabularKind ->
                    Tabular(HeaderListing.ofCollection(
                        map.requiredList(DataModelKeys.header).values.mapIndexed { index, item ->
                            (item as? TextExecutionValue)?.value
                                ?: throw IllegalArgumentException(
                                    "'${DataModelKeys.header}[$index]' must be text: $map")
                        }
                    ))

                DataModelKeys.payloadKind ->
                    Payload(typeMetadataOfExecutionValue(map.requiredMap(DataModelKeys.type)))

                else ->
                    throw IllegalArgumentException("Unknown '${DataModelKeys.kind}': $kind")
            }
        }
    }


    fun asExecutionValue(): ExecutionValue {
        return when (this) {
            is Tabular -> MapExecutionValue(linkedMapOf(
                DataModelKeys.kind to TextExecutionValue(DataModelKeys.tabularKind),
                DataModelKeys.header to ListExecutionValue(
                    header.asCollection().map(::TextExecutionValue)
                )
            ))

            is Payload -> MapExecutionValue(linkedMapOf(
                DataModelKeys.kind to TextExecutionValue(DataModelKeys.payloadKind),
                DataModelKeys.type to type.asExecutionValue()
            ))
        }
    }
}


@Serializable
private data class DataShapeWire(
    @SerialName(DataModelKeys.kind)
    val kind: String,
    @SerialName(DataModelKeys.header)
    val header: List<String>? = null,
    @SerialName(DataModelKeys.type)
    val type: TypeMetadataWire? = null
)


@Serializable
private data class TypeMetadataWire(
    @SerialName(DataModelKeys.className)
    val `class`: String,
    @SerialName(DataModelKeys.generics)
    val generics: List<TypeMetadataWire>,
    @SerialName(DataModelKeys.nullable)
    val nullable: Boolean
)


object DataShapeSerializer: KSerializer<DataShape> {
    override val descriptor = DataShapeWire.serializer().descriptor


    override fun serialize(encoder: Encoder, value: DataShape) {
        val wire = when (value) {
            is DataShape.Tabular -> DataShapeWire(
                DataModelKeys.tabularKind,
                header = value.header.asCollection()
            )

            is DataShape.Payload -> DataShapeWire(
                DataModelKeys.payloadKind,
                type = value.type.asWire()
            )
        }
        encoder.encodeSerializableValue(DataShapeWire.serializer(), wire)
    }


    override fun deserialize(decoder: Decoder): DataShape {
        val wire = decoder.decodeSerializableValue(DataShapeWire.serializer())
        return when (wire.kind) {
            DataModelKeys.tabularKind -> DataShape.Tabular(
                HeaderListing.ofCollection(
                    requireNotNull(wire.header) { "'${DataModelKeys.header}' missing for ${wire.kind}" }
                )
            )

            DataModelKeys.payloadKind -> DataShape.Payload(
                requireNotNull(wire.type) { "'${DataModelKeys.type}' missing for ${wire.kind}" }.asTypeMetadata()
            )

            else -> throw IllegalArgumentException("Unknown '${DataModelKeys.kind}': ${wire.kind}")
        }
    }
}


private fun TypeMetadata.asWire(): TypeMetadataWire {
    return TypeMetadataWire(
        className.asString(),
        generics.map(TypeMetadata::asWire),
        nullable
    )
}


private fun TypeMetadataWire.asTypeMetadata(): TypeMetadata {
    return TypeMetadata(
        ClassName(`class`),
        generics.map(TypeMetadataWire::asTypeMetadata),
        nullable
    )
}


private fun typeMetadataOfExecutionValue(value: MapExecutionValue): TypeMetadata {
    val className = value.requiredText(DataModelKeys.className)
    val genericValues = value.requiredList(DataModelKeys.generics).values
    val generics = genericValues.mapIndexed { index, genericValue ->
        val genericMap = genericValue as? MapExecutionValue
            ?: throw IllegalArgumentException(
                "'${DataModelKeys.generics}[$index]' must be a map: $value")
        typeMetadataOfExecutionValue(genericMap)
    }
    val nullableValue = value.requiredValue(DataModelKeys.nullable)
    val nullable = (nullableValue as? BooleanExecutionValue)?.value
        ?: throw IllegalArgumentException("'${DataModelKeys.nullable}' must be boolean: $value")

    return TypeMetadata(ClassName(className), generics, nullable)
}
