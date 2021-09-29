package tech.kzen.auto.client.objects.document.pipeline.model

import tech.kzen.auto.common.objects.document.report.listing.AnalysisColumnInfo
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing


class PipelineStateCache {
    //-----------------------------------------------------------------------------------------------------------------
    private var cachedColumnListing: AnalysisColumnInfo? = null
    private var cachedFormulaKeys: Set<String>? = null

    private var inputColumnNamesCache: List<String>? = null
//    private var filteredColumnsCache: List<String>? = null
    private var inputAndCalculatedColumnsCache: HeaderListing? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun inputColumnNames(state: PipelineState): List<String>? {
        update(state)
        return inputColumnNamesCache
    }


    fun inputAndCalculatedColumns(state: PipelineState): HeaderListing? {
        update(state)
        return inputAndCalculatedColumnsCache
    }


    fun analysisColumnInfo(state: PipelineState): AnalysisColumnInfo? {
        update(state)
        return cachedColumnListing
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun update(state: PipelineState) {
        val columnListing = state.input.column.columnListing
        val formulaKeys = state.formulaSpec().formulas.keys

        if (columnListing === cachedColumnListing && formulaKeys === cachedFormulaKeys) {
            return
        }

        cachedColumnListing = columnListing
        cachedFormulaKeys = formulaKeys

        if (columnListing == null) {
            inputColumnNamesCache = null
            inputAndCalculatedColumnsCache = null
            return
        }

        val allColumns = columnListing.inputColumns.keys.toList()
        inputColumnNamesCache = allColumns
        inputAndCalculatedColumnsCache = HeaderListing(allColumns + formulaKeys)
    }
}