package tech.kzen.auto.common.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.common.data.read.ResolvedReadSpec


object DataPartSerializer: KSerializer<DataPart> {
    override val descriptor = DataPartWire.serializer().descriptor

    override fun serialize(encoder: Encoder, value: DataPart) {
        encoder.encodeSerializableValue(
            DataPartWire.serializer(),
            DataPartWire(value.role, value.ref, value.expectedFingerprint, value.resolvedRead))
    }

    override fun deserialize(decoder: Decoder): DataPart {
        val wire = decoder.decodeSerializableValue(DataPartWire.serializer())
        return DataPart(wire.role, wire.ref, wire.expectedFingerprint, wire.resolvedRead)
    }
}


@Serializable
private data class DataPartWire(
    @SerialName(DataModelKeys.role)
    val role: DataRole,
    @SerialName(DataModelKeys.ref)
    val ref: DataRef,
    @SerialName(DataModelKeys.expectedFingerprint)
    val expectedFingerprint: DataContentFingerprint?,
    @SerialName(DataModelKeys.resolvedRead)
    val resolvedRead: ResolvedReadSpec
)
