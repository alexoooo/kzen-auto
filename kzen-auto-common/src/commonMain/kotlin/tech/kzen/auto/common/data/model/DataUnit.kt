package tech.kzen.auto.common.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


/**
 * One unit of work: presentation-ordered attributes plus a semantically ordered part list.
 * Attribute order is preserved as supplied but is not semantic; see
 * `kzen/docs/analysis/2026-08-20_job-data-source.md` §3.4–3.5.
 */
@Serializable(with = DataUnitSerializer::class)
data class DataUnit(
    val attributes: Map<String, String>,
    val parts: List<DataPart>
): Digestible {
    companion object {
        fun ofExecutionValue(value: ExecutionValue): DataUnit {
            val map = value.requiredModelMap("DataUnit")
            return DataUnit(
                map.requiredTextMap(DataModelKeys.attributes),
                map.requiredList(DataModelKeys.parts).values.map(DataPart::ofExecutionValue)
            )
        }


        fun of(vararg parts: DataPart): DataUnit {
            return DataUnit(emptyMap(), parts.toList())
        }


        fun of(attributes: Map<String, String>, parts: List<DataPart>): DataUnit {
            return DataUnit(attributes, parts)
        }
    }


    fun partsOf(role: DataRole): List<DataPart> {
        return parts.filter { it.role == role }
    }


    fun part(role: DataRole): DataPart {
        val matching = partsOf(role)
        check(matching.size == 1) {
            "Expected exactly one part for role '${role.name}', found ${matching.size}"
        }
        return matching.single()
    }


    val isSingleRole: Boolean
        get() = parts.isNotEmpty() && parts.all { it.role == parts.first().role }


    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(linkedMapOf(
            DataModelKeys.attributes to textExecutionMap(attributes),
            DataModelKeys.parts to ListExecutionValue(parts.map { it.asExecutionValue() })
        ))
    }


    override fun digest(sink: Digest.Sink) {
        sink.addInt(attributes.size)
        for (key in attributes.keys.sorted()) {
            sink.addUtf8(key)
            sink.addUtf8(attributes.getValue(key))
        }
        sink.addDigestibleList(parts)
    }
}


@Serializable
private data class DataUnitWire(
    @SerialName(DataModelKeys.attributes)
    val attributes: Map<String, String>,
    @SerialName(DataModelKeys.parts)
    val parts: List<DataPart>
)


object DataUnitSerializer: KSerializer<DataUnit> {
    override val descriptor = DataUnitWire.serializer().descriptor


    override fun serialize(encoder: Encoder, value: DataUnit) {
        encoder.encodeSerializableValue(
            DataUnitWire.serializer(),
            DataUnitWire(value.attributes, value.parts)
        )
    }


    override fun deserialize(decoder: Decoder): DataUnit {
        val wire = decoder.decodeSerializableValue(DataUnitWire.serializer())
        val attributes = linkedMapOf<String, String>()
        attributes.putAll(wire.attributes)
        return DataUnit(attributes, wire.parts)
    }
}
