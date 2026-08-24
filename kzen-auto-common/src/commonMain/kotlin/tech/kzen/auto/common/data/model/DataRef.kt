package tech.kzen.auto.common.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


/**
 * Reference to one readable value; a null [source] is the plain, self-contained form used by file paths.
 * [attributes] iteration order is preserved as supplied for display but is not semantic; see
 * `kzen/docs/analysis/2026-08-20_job-data-source.md` §3.3–3.5.
 */
@Serializable(with = DataRefSerializer::class)
data class DataRef(
    val source: DataSourceId?,
    val id: String,
    val attributes: Map<String, String> = emptyMap()
): Digestible {
    companion object {
        const val sizeKey = "size"
        const val modifiedKey = "modified"


        fun ofExecutionValue(value: ExecutionValue): DataRef {
            val map = value.requiredModelMap("DataRef")
            return DataRef(
                map.requiredNullableText(DataModelKeys.source)?.let(::DataSourceId),
                map.requiredText(DataModelKeys.id),
                map.requiredTextMap(DataModelKeys.attributes)
            )
        }


        fun ofLocation(location: DataLocation): DataRef {
            return DataRef(null, location.asString())
        }


        fun of(info: DataLocationInfo): DataRef {
            return DataRef(
                null,
                info.path.asString(),
                linkedMapOf(
                    sizeKey to info.size.toString(),
                    modifiedKey to info.modified.toString()))
        }
    }


    fun display(): String {
        return id
    }


    fun fingerprintOrNull(): Pair<String, String>? {
        val size = attributes[sizeKey] ?: return null
        val modified = attributes[modifiedKey] ?: return null
        return size to modified
    }


    fun asLocationOrNull(): DataLocation? {
        return if (source == null) DataLocation.parse(id) else null
    }


    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(linkedMapOf(
            DataModelKeys.source to (source?.let { TextExecutionValue(it.value) } ?: NullExecutionValue),
            DataModelKeys.id to TextExecutionValue(id),
            DataModelKeys.attributes to textExecutionMap(attributes)
        ))
    }


    override fun digest(sink: Digest.Sink) {
        sink.addUtf8Nullable(source?.value)
        sink.addUtf8(id)
        sink.addInt(attributes.size)
        for (key in attributes.keys.sorted()) {
            sink.addUtf8(key)
            sink.addUtf8(attributes.getValue(key))
        }
    }
}


@Serializable
private data class DataRefWire(
    @SerialName(DataModelKeys.source)
    val source: DataSourceId?,
    @SerialName(DataModelKeys.id)
    val id: String,
    @SerialName(DataModelKeys.attributes)
    val attributes: Map<String, String>
)


object DataRefSerializer: KSerializer<DataRef> {
    override val descriptor = DataRefWire.serializer().descriptor


    override fun serialize(encoder: Encoder, value: DataRef) {
        encoder.encodeSerializableValue(
            DataRefWire.serializer(),
            DataRefWire(value.source, value.id, value.attributes)
        )
    }


    override fun deserialize(decoder: Decoder): DataRef {
        val wire = decoder.decodeSerializableValue(DataRefWire.serializer())
        val attributes = linkedMapOf<String, String>()
        attributes.putAll(wire.attributes)
        return DataRef(wire.source, wire.id, attributes)
    }
}
