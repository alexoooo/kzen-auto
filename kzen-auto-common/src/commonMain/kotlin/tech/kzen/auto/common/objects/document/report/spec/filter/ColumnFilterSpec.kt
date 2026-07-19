package tech.kzen.auto.common.objects.document.report.spec.filter

import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.codec.NotationCodec
import tech.kzen.lib.common.model.structure.notation.codec.NotationCodecs
import tech.kzen.lib.common.model.structure.notation.codec.field
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class ColumnFilterSpec(
    val type: ColumnFilterType,
    val values: Set<String>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = ColumnFilterSpec(ColumnFilterType.RequireAny, setOf())

        private val typeKey = "type"
        private val valuesKey = "values"

        val typeAttributeSegment = AttributeSegment.ofKey(typeKey)
        val valuesAttributeSegment = AttributeSegment.ofKey(valuesKey)

        private val typeCodec = NotationCodecs.enum<ColumnFilterType>()
        private val valuesCodec = NotationCodecs.set(NotationCodecs.scalar)

        // A column criteria is a `{type, values}` record. This codec is the single source of truth for that
        // key layout — the read side ([ofNotation], also used by the JS ValueSetFilterEditor) and the write
        // side ([emptyNotation], and FilterSpec's per-field command builders) both derive from it.
        val codec: NotationCodec<ColumnFilterSpec> = NotationCodecs.record(
            decode = {
                ColumnFilterSpec(
                    it.field(typeKey, typeCodec),
                    it.field(valuesKey, valuesCodec))
            },
            encode = {
                listOf(
                    typeKey to typeCodec.unparse(it.type),
                    valuesKey to valuesCodec.unparse(it.values))
            })

        val emptyNotation: MapAttributeNotation = codec.unparse(empty) as MapAttributeNotation


        fun ofNotation(attributeNotation: MapAttributeNotation): ColumnFilterSpec {
            return codec.parse(attributeNotation)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return values.isEmpty()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addInt(type.ordinal)
        sink.addUnorderedCollection(values) { addUtf8(it) }
    }
}