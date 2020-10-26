package tech.kzen.auto.common.objects.document.process

import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class PivotValueSpec(
    val types: Set<PivotValueType>
):
    Digestible
{
    companion object {
        fun ofNotation(notation: ListAttributeNotation): PivotValueSpec {
            val types = notation
                .values
                .mapNotNull { it.asString() }
                .map { PivotValueType.valueOf(it) }
                .toSet()
            return PivotValueSpec(types)
        }
    }


    override fun digest(builder: Digest.Builder) {
        builder.addDigestibleUnorderedList(types.map { Digest.ofInt(it.ordinal) })
    }
}