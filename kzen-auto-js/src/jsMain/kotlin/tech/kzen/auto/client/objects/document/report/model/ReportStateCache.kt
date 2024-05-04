package tech.kzen.auto.client.objects.document.report.model

import tech.kzen.auto.common.objects.document.report.listing.AnalysisColumnInfo
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing


class ReportStateCache {
    //-----------------------------------------------------------------------------------------------------------------
    private var cachedAnalysisColumnInfo: AnalysisColumnInfo? = null
    private var cachedFormulaKeys: Set<String>? = null

    private var inputColumnNamesCache: HeaderListing? = null
    private var inputAndCalculatedColumnsCache: HeaderListing? = null
    private var filteredColumnsCache: HeaderListing? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun inputColumnNames(state: ReportState): HeaderListing? {
        update(state)
        return inputColumnNamesCache
    }


    fun inputAndCalculatedColumns(state: ReportState): HeaderListing? {
        update(state)
        return inputAndCalculatedColumnsCache
    }


    fun filteredColumns(state: ReportState): HeaderListing? {
        update(state)
        return filteredColumnsCache
    }


    fun analysisColumnInfo(state: ReportState): AnalysisColumnInfo? {
        update(state)
        return cachedAnalysisColumnInfo
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun update(state: ReportState) {
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

        val inputColumnsHeaderListing = analysisColumnInfo.inputColumns.allHeaderListing()
        inputColumnNamesCache = inputColumnsHeaderListing

        val calculatedColumnsHeaderListing = analysisColumnInfo.calculatedColumns.allHeaderListing()
        inputAndCalculatedColumnsCache = inputColumnsHeaderListing.append(calculatedColumnsHeaderListing)

        filteredColumnsCache = analysisColumnInfo.filteredInputAndCalculatedColumns
    }
}