package tech.kzen.auto.common.objects.document.report.spec.analysis.pivot

import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.codec.NotationCodec
import tech.kzen.lib.common.model.structure.notation.codec.NotationCodecs
import tech.kzen.lib.common.model.structure.notation.codec.xmap
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class PivotValueColumnSpec(
    val types: Set<PivotValueType>
):
    Digestible
{
    companion object {
        // A per-column list of aggregate value types (Count / Sum / ...), collected into a set.
        val codec: NotationCodec<PivotValueColumnSpec> =
            NotationCodecs.set(NotationCodecs.enum<PivotValueType>())
                .xmap({ PivotValueColumnSpec(it) }, { it.types })


        fun ofNotation(notation: ListAttributeNotation): PivotValueColumnSpec {
            return codec.parse(notation)
        }
    }


    override fun digest(sink: Digest.Sink) {
        sink.addUnorderedCollection(types) { addInt(it.ordinal) }
    }
}