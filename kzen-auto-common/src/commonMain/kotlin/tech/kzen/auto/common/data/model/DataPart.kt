package tech.kzen.auto.common.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


/**
 * One ordered, role-labelled readable value and its optional decoding hints; see
 * `kzen/docs/analysis/2026-08-20_job-data-source.md` §3.2.
 */
@Serializable(with = DataPartSerializer::class)
data class DataPart(
    val role: DataRole,
    val ref: DataRef,
    val format: CommonPluginCoordinate?,
    val encoding: CommonDataEncodingSpec?
): Digestible {
    companion object {
        fun ofExecutionValue(value: ExecutionValue): DataPart {
            val map = value.requiredModelMap("DataPart")
            return DataPart(
                DataRole(map.requiredText(DataModelKeys.role)),
                DataRef.ofExecutionValue(map.requiredMap(DataModelKeys.ref)),
                map.requiredNullableText(DataModelKeys.format)?.let(CommonPluginCoordinate::ofString),
                map.requiredNullableText(DataModelKeys.encoding)?.let(CommonDataEncodingSpec::ofString)
            )
        }


        fun ofPath(role: DataRole, path: String): DataPart {
            return DataPart(role, DataRef(null, path), null, null)
        }
    }


    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(linkedMapOf(
            DataModelKeys.role to TextExecutionValue(role.name),
            DataModelKeys.ref to ref.asExecutionValue(),
            DataModelKeys.format to (format?.let { TextExecutionValue(it.asString()) } ?: NullExecutionValue),
            DataModelKeys.encoding to (encoding?.let { TextExecutionValue(it.asString()) } ?: NullExecutionValue)
        ))
    }


    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(role.name)
        sink.addDigestible(ref)
        sink.addUtf8Nullable(format?.asString())
        sink.addUtf8Nullable(encoding?.asString())
    }
}


@Serializable
private data class DataPartWire(
    @SerialName(DataModelKeys.role)
    val role: DataRole,
    @SerialName(DataModelKeys.ref)
    val ref: DataRef,
    @SerialName(DataModelKeys.format)
    val format: String?,
    @SerialName(DataModelKeys.encoding)
    val encoding: String?
)


object DataPartSerializer: KSerializer<DataPart> {
    override val descriptor = DataPartWire.serializer().descriptor


    override fun serialize(encoder: Encoder, value: DataPart) {
        encoder.encodeSerializableValue(
            DataPartWire.serializer(),
            DataPartWire(
                value.role,
                value.ref,
                value.format?.asString(),
                value.encoding?.asString()
            )
        )
    }


    override fun deserialize(decoder: Decoder): DataPart {
        val wire = decoder.decodeSerializableValue(DataPartWire.serializer())
        return DataPart(
            wire.role,
            wire.ref,
            wire.format?.let(CommonPluginCoordinate::ofString),
            wire.encoding?.let(CommonDataEncodingSpec::ofString)
        )
    }
}
