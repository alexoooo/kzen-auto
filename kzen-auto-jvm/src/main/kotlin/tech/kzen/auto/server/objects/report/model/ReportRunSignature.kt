package tech.kzen.auto.server.objects.report.model

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.server.objects.report.pipeline.input.v2.model.DatasetInfo
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class ReportRunSignature(
    val datasetInfo: DatasetInfo,
    val inputAndFormulaColumns: HeaderListing,
    val formula: FormulaSpec,
    val nonEmptyFilter: FilterSpec,
    val pivotRows: HeaderListing,
    val pivotValues: HeaderListing
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
//    val inputAndFormulaColumns: HeaderListing by lazy {
//        datasetInfo.headerSuperset().append(formula.headerListing())
//    }

//    fun inputColumns(): HeaderListing {
//        return HeaderListing(inputs.flatMap { it.headerListing.values }.toSet().toList())
//    }


//    fun formulaColumns(): HeaderListing {
//        return HeaderListing(formula.formulas.keys.toList())
//    }


//    fun inputAndFormulaColumns(): HeaderListing {
//        return datasetInfo.headerSuperset().append(formula.headerListing())
//    }


    //-----------------------------------------------------------------------------------------------------------------
    fun hasPivot(): Boolean {
        return pivotRows.values.isNotEmpty() ||
                pivotValues.values.isNotEmpty()
    }


    override fun digest(builder: Digest.Builder) {
        builder.addDigestible(datasetInfo)
//        builder.addDigestibleList(inputs.map { Digest.ofUtf8(it.toString()) })
//        builder.addDigestibleList(inputColumns().values.map { Digest.ofUtf8(it) })

        builder.addDigestible(formula)
        builder.addDigestible(nonEmptyFilter)

        builder.addDigestibleUnorderedList(pivotRows.values.map { Digest.ofUtf8(it) })
        builder.addDigestibleUnorderedList(pivotValues.values.map { Digest.ofUtf8(it) })
    }
}