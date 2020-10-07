package tech.kzen.auto.common.objects.document.process

import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation


data class PivotValueSpec(
    val types: Set<PivotValueType>
) {
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
}