package tech.kzen.auto.client.objects.document.report.state

import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.listing.InputSelectionInfo
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.progress.ReportProgress
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.PreviewSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.locate.ObjectLocation


data class ReportState(
    val clientState: SessionState,
    val mainLocation: ObjectLocation,

    val inputLoaded: Boolean = false,
    val inputLoading: Boolean = false,
    val inputSelection: InputSelectionInfo? = null,
    val inputBrowser: List<DataLocationInfo>? = null,
    val inputBrowseDir: DataLocation? = null,
    val inputError: String? = null,

    val columnListingLoaded: Boolean = false,
    val columnListingLoading: Boolean = false,
    val columnListing: List<String>? = null,
    val columnListingError: String? = null,

    val formulaLoading: Boolean = false,
    val formulaError: String? = null,
    val formulaMessages: Map<String, String> = mapOf(),

    val filterLoading: Boolean = false,
    val filterError: String? = null,

    val pivotLoading: Boolean = false,
    val pivotError: String? = null,

    val outputLoaded: Boolean = false,
    val outputLoading: Boolean = false,
    val outputInfo: OutputInfo? = null,
    val outputError: String? = null,

    val taskLoaded: Boolean = false,
    val taskLoading: Boolean = false,
    val taskStarting: Boolean = false,
    val taskStopping: Boolean = false,
    val taskRunning: Boolean = false,
    val taskModel: TaskModel? = null,
    val reportProgress: ReportProgress? = null,
    val taskLoadError: String? = null,
    val taskError: String? = null,

    val tableSummaryLoaded: Boolean = false,
    val tableSummaryLoading: Boolean = false,
    val tableSummary: TableSummary? = null,
    val tableSummaryError: String? = null
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun tryCreate(clientState: SessionState): ReportState? {
            val mainLocation = tryMainLocation(clientState)
                ?: return null

            return ReportState(clientState, mainLocation)
        }


        fun tryMainLocation(clientState: SessionState): ObjectLocation? {
            val documentPath = clientState
                .navigationRoute
                .documentPath
                ?: return null

            val documentNotation = clientState
                .graphStructure()
                .graphNotation
                .documents[documentPath]
                ?: return null

            if (! ReportConventions.isReport(documentNotation)) {
                return null
            }

            return documentPath.toMainObjectLocation()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var selectedPathSet: Set<DataLocation>? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun isInitiating(): Boolean {
        if (inputSpec().selection.locations.isEmpty()) {
            return false
        }

        if (! inputLoaded) {
            return true
        }

        if (! columnListingLoaded) {
            return true
        }

        if (columnListing.isNullOrEmpty()) {
            return false
        }

        return ! taskLoaded ||
                ! outputLoaded ||
                ! tableSummaryLoaded
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isTaskRunning(): Boolean {
//        return indexTaskRunning || filterTaskRunning
        return taskRunning
    }


//    fun isInitialLoading(): Boolean {
//        return fileListingLoading || columnListingLoading || taskLoading || outputLoading
//    }


    fun isLoadingError(): Boolean {
        return columnListingError != null ||
                inputError != null ||
                taskLoadError != null ||
                tableSummaryError != null
    }


    fun nextErrorMessage(): String? {
        return columnListingError
            ?: inputError
            ?: taskLoadError
            ?: taskError
            ?: tableSummaryError
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun reportDefinition(): ObjectDefinition {
        return clientState
            .graphDefinitionAttempt
            .objectDefinitions[mainLocation]!!
    }


    fun inputSpec(): InputSpec {
        val formulaDefinition = reportDefinition().attributeDefinitions[ReportConventions.inputAttributeName]!!
        return (formulaDefinition as ValueAttributeDefinition).value as InputSpec
    }


    fun formulaSpec(): FormulaSpec {
        val formulaDefinition = reportDefinition().attributeDefinitions[ReportConventions.formulaAttributeName]!!
        return (formulaDefinition as ValueAttributeDefinition).value as FormulaSpec
    }


    fun previewSpec(filtered: Boolean): PreviewSpec {
        return when (filtered) {
            false -> previewAllSpec()
            true -> previewFilteredSpec()
        }
    }


    fun previewAllSpec(): PreviewSpec {
        val previewDefinition = reportDefinition().attributeDefinitions[ReportConventions.previewFilteredAttributeName]!!
        return (previewDefinition as ValueAttributeDefinition).value as PreviewSpec
    }


    fun previewFilteredSpec(): PreviewSpec {
        val previewDefinition = reportDefinition().attributeDefinitions[ReportConventions.previewFilteredAttributeName]!!
        return (previewDefinition as ValueAttributeDefinition).value as PreviewSpec
    }


    fun filterSpec(): FilterSpec {
        val filterDefinition = reportDefinition().attributeDefinitions[ReportConventions.filterAttributeName]!!
        return (filterDefinition as ValueAttributeDefinition).value as FilterSpec
    }


    fun analysisSpec(): AnalysisSpec {
        val pivotDefinition = reportDefinition().attributeDefinitions[ReportConventions.analysisAttributeName]!!
        return (pivotDefinition as ValueAttributeDefinition).value as AnalysisSpec
    }


    fun outputSpec(): OutputSpec {
        val outputDefinition = reportDefinition().attributeDefinitions[ReportConventions.outputAttributeName]!!
        return (outputDefinition as ValueAttributeDefinition).value as OutputSpec
    }


    fun inputAndCalculatedColumns(): HeaderListing? {
        if (columnListing == null) {
            return null
        }
        return HeaderListing(columnListing + formulaSpec().formulas.keys)
    }


    fun selectedPathSet(): Set<DataLocation> {
        if (selectedPathSet == null) {
            selectedPathSet = inputSpec().selection.locations.map { it.location }.toSet()
        }
        return selectedPathSet!!
    }


    fun outputCount(): Long {
        val progressOutputCount = reportProgress?.outputCount ?: 0
        val infoOutputCount = outputInfo?.rowCount ?: 0
        return progressOutputCount.coerceAtLeast(infoOutputCount)
    }
}