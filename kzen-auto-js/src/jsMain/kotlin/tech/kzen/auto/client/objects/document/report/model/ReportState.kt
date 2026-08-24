package tech.kzen.auto.client.objects.document.report.model

import tech.kzen.auto.client.objects.document.report.filter.model.ReportFilterState
import tech.kzen.auto.client.objects.document.report.formula.model.ReportFormulaState
import tech.kzen.auto.client.objects.document.report.input.browse.model.InputBrowserState
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputState
import tech.kzen.auto.client.objects.document.report.input.select.model.InputSelectedState
import tech.kzen.auto.client.objects.document.report.output.model.ReportOutputState
import tech.kzen.auto.client.objects.document.report.preview.model.ReportPreviewState
import tech.kzen.auto.client.objects.document.report.run.model.ReportRunState
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.logic.ClientLogicState
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.AnalysisColumnInfo
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.PreviewSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinition
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.location.ObjectLocation


data class ReportState(
    val mainLocation: ObjectLocation,
    val mainDefinition: ObjectDefinition,
    val clientLogicState: ClientLogicState,
    val input: ReportInputState = ReportInputState(),
    val formula: ReportFormulaState = ReportFormulaState(),
    val filter: ReportFilterState = ReportFilterState(),
    val previewFiltered: ReportPreviewState = ReportPreviewState(),
    val output: ReportOutputState = ReportOutputState(),
    val run: ReportRunState = ReportRunState(),
    val notationError: String? = null,

    @Suppress("PropertyName")
    val _cache: ReportStateCache = ReportStateCache()
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun tryMainLocation(clientState: ClientState): ObjectLocation? {
            val documentPath = clientState
                .navigationRoute
                .documentPath
                ?: return null

            val documentNotation = clientState
                .graphStructure()
                .graphNotation
                .documents[documentPath]
                ?: return null

            if (!ReportConventions.isReport(documentNotation)) {
                return null
            }

            return documentPath.toMainObjectLocation()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Belt-and-braces: ReportStore only builds a state over a fully defined `main`, so a missing spec attribute is
    // unreachable - if it ever happens, say which attribute rather than throwing an anonymous NPE.
    private fun attributeDefinition(attributeName: AttributeName): AttributeDefinition {
        return mainDefinition.attributeDefinitions[attributeName]
            ?: throw IllegalStateException(
                "Report attribute '${attributeName.value}' is not defined for $mainLocation " +
                "- fix the document notation")
    }


    fun inputSpec(): InputSpec {
        val definition = attributeDefinition(ReportConventions.inputAttributeName)
        return (definition as ValueAttributeDefinition).value as InputSpec
    }


    fun formulaSpec(): FormulaSpec {
        val definition = attributeDefinition(ReportConventions.formulaAttributeName)
        return (definition as ValueAttributeDefinition).value as FormulaSpec
    }


    fun filterSpec(): FilterSpec {
        val definition = attributeDefinition(ReportConventions.filterAttributeName)
        return (definition as ValueAttributeDefinition).value as FilterSpec
    }


    fun previewSpec(filtered: Boolean): PreviewSpec {
        return when (filtered) {
            true -> previewFilteredSpec()
            false -> previewAllSpec()
        }
    }


    fun previewAllSpec(): PreviewSpec {
        val definition = attributeDefinition(ReportConventions.previewFilteredAttributeName)
        return (definition as ValueAttributeDefinition).value as PreviewSpec
    }


    fun previewFilteredSpec(): PreviewSpec {
        val definition = attributeDefinition(ReportConventions.previewFilteredAttributeName)
        return (definition as ValueAttributeDefinition).value as PreviewSpec
    }


    fun analysisSpec(): AnalysisSpec {
        val definition = attributeDefinition(ReportConventions.analysisAttributeName)
        return (definition as ValueAttributeDefinition).value as AnalysisSpec
    }


    fun outputSpec(): OutputSpec {
        val definition = attributeDefinition(ReportConventions.outputAttributeName)
        return (definition as ValueAttributeDefinition).value as OutputSpec
    }


    fun isRunningOrLoading(): Boolean {
        return isRunning()
    }


    fun isRunning(): Boolean {
//        return run.logicStatus?.active != null
        return clientLogicState.isActive()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun inputColumnNames(): HeaderListing? {
        return _cache.inputColumnNames(this)
    }


    fun inputAndCalculatedColumns(): HeaderListing? {
        return _cache.inputAndCalculatedColumns(this)
    }


    fun filteredColumns(): HeaderListing? {
        return _cache.filteredColumns(this)
    }


    fun analysisColumnInfo(): AnalysisColumnInfo? {
        return _cache.analysisColumnInfo(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withNotationError(notationError: String?): ReportState {
        return copy(
            notationError = notationError)
    }


    fun withInputBrowser(updater: (InputBrowserState) -> InputBrowserState): ReportState {
        return copy(
            input = input.copy(
                browser = updater(input.browser)))
    }


    fun withInputSelected(updater: (InputSelectedState) -> InputSelectedState): ReportState {
        return copy(
            input = input.copy(
                selected = updater(input.selected)))
    }


    fun withFormula(updater: (ReportFormulaState) -> ReportFormulaState): ReportState {
        return copy(
            formula = updater(formula))
    }


    fun withFilter(updater: (ReportFilterState) -> ReportFilterState): ReportState {
        return copy(
            filter = updater(filter))
    }


    fun withPreviewFiltered(updater: (ReportPreviewState) -> ReportPreviewState): ReportState {
        return copy(
            previewFiltered = updater(previewFiltered))
    }


    fun withOutput(updater: (ReportOutputState) -> ReportOutputState): ReportState {
        return copy(
            output = updater(output))
    }


    fun withRun(updater: (ReportRunState) -> ReportRunState): ReportState {
        return copy(
            run = updater(run))
    }
}
