package tech.kzen.auto.client.objects.document.pipeline.model

import tech.kzen.auto.common.objects.document.report.listing.AnalysisColumnInfo
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing


class PipelineStateCache {
    //-----------------------------------------------------------------------------------------------------------------
    private var cachedAnalysisColumnInfo: AnalysisColumnInfo? = null
    private var cachedFormulaKeys: Set<String>? = null

    private var inputColumnNamesCache: List<String>? = null
    private var inputAndCalculatedColumnsCache: HeaderListing? = null
    private var filteredColumnsCache: HeaderListing? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun inputColumnNames(state: PipelineState): List<String>? {
        update(state)
        return inputColumnNamesCache
    }


    fun inputAndCalculatedColumns(state: PipelineState): HeaderListing? {
        update(state)
        return inputAndCalculatedColumnsCache
    }


    fun filteredColumns(state: PipelineState): HeaderListing? {
        update(state)
        return filteredColumnsCache
    }


    fun analysisColumnInfo(state: PipelineState): AnalysisColumnInfo? {
        update(state)
        return cachedAnalysisColumnInfo
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun update(state: PipelineState) {
        val analysisColumnInfo = state.input.column.analysisColumnInfo
        val formulaKeys = state.formulaSpec().formulas.keys

        if (analysisColumnInfo === cachedAnalysisColumnInfo && formulaKeys === cachedFormulaKeys) {
            return
        }

        cachedAnalysisColumnInfo = analysisColumnInfo
        cachedFormulaKeys = formulaKeys

        if (analysisColumnInfo == null) {
            inputColumnNamesCache = null
            inputAndCalculatedColumnsCache = null
            filteredColumnsCache = null
            return
        }

        val inputAndCalculatedColumnNames = analysisColumnInfo.inputAndCalculatedColumns.keys.toList()
        inputAndCalculatedColumnsCache = HeaderListing(inputAndCalculatedColumnNames)

        val inputColumnNames = inputAndCalculatedColumnNames.filterNot { it in formulaKeys }
        inputColumnNamesCache = inputColumnNames

        filteredColumnsCache = analysisColumnInfo.filteredColumns()
    }
}