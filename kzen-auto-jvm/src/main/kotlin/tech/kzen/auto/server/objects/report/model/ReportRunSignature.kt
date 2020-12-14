package tech.kzen.auto.server.objects.report.model

import tech.kzen.auto.common.objects.document.report.spec.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible
import java.nio.file.Path


data class ReportRunSignature(
    val inputs: List<Path>,
    val columnNames: List<String>,
    val formula: FormulaSpec,
    val nonEmptyFilter: FilterSpec,
    val pivotRows: Set<String>,
    val pivotValues: Set<String>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    fun hasPivot(): Boolean {
        return pivotRows.isNotEmpty() ||
                pivotValues.isNotEmpty()
    }


    override fun digest(builder: Digest.Builder) {
        builder.addDigestibleList(inputs.map { Digest.ofUtf8(it.toString()) })
        builder.addDigestibleList(columnNames.map { Digest.ofUtf8(it) })

        builder.addDigestible(formula)
        builder.addDigestible(nonEmptyFilter)

        builder.addDigestibleUnorderedList(pivotRows.map { Digest.ofUtf8(it) })
        builder.addDigestibleUnorderedList(pivotValues.map { Digest.ofUtf8(it) })
    }
}