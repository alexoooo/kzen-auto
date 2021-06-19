package tech.kzen.auto.common.objects.document.report.spec.analysis

import tech.kzen.auto.common.objects.document.report.spec.filter.ColumnFilterSpec
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class AnalysisFlatDataSpec(
    val rowFilter: ColumnFilterSpec
): Digestible {
    companion object {
        val empty = AnalysisFlatDataSpec(ColumnFilterSpec.empty)

        fun ofNotation(attributeNotation: MapAttributeNotation): AnalysisFlatDataSpec {
            val rowFilter = ColumnFilterSpec.ofNotation(attributeNotation)
            return AnalysisFlatDataSpec(rowFilter)
        }
    }


    override fun digest(builder: Digest.Builder) {
        builder.addDigestible(rowFilter)
    }
}