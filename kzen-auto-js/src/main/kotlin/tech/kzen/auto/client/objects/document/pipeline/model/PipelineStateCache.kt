package tech.kzen.auto.client.objects.document.pipeline.model

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing


class PipelineStateCache {
    private var cachedColumnListing: List<String>? = null
    private var cachedFormulaKeys: Set<String>? = null
    private var inputAndCalculatedColumnsCache: HeaderListing? = null


    fun inputAndCalculatedColumns(state: PipelineState): HeaderListing? {
        val columnListing = state.input.column.columnListing
        val formulaKeys = state.formulaSpec().formulas.keys

        if (columnListing === cachedColumnListing && formulaKeys === cachedFormulaKeys) {
            return inputAndCalculatedColumnsCache
        }

        cachedColumnListing = columnListing
        cachedFormulaKeys = formulaKeys

        inputAndCalculatedColumnsCache =
            if (columnListing == null) {
                return null
            }
            else {
                HeaderListing(columnListing + formulaKeys)
            }

        return inputAndCalculatedColumnsCache
    }
}