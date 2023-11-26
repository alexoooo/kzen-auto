package tech.kzen.auto.common.objects.document.report.spec.analysis.pivot

import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class PivotValueColumnSpec(
    val types: Set<PivotValueType>
):
    Digestible
{
    companion object {
        fun ofNotation(notation: ListAttributeNotation): PivotValueColumnSpec {
            val types = notation
                .values
                .mapNotNull { it.asString() }
                .map { PivotValueType.valueOf(it) }
                .toSet()
            return PivotValueColumnSpec(types)
        }
    }


    override fun digest(sink: Digest.Sink) {
        sink.addUnorderedCollection(types) { addInt(it.ordinal) }
    }
}