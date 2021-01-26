package tech.kzen.auto.client.objects.document.report.state

import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.FileInfo
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.spec.*
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.auto.common.paradigm.task.model.TaskProgress
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.notation.NotationConventions


data class ReportState(
    val clientState: SessionState,
    val mainLocation: ObjectLocation,

    val inputLoaded: Boolean = false,
    val inputLoading: Boolean = false,
    val inputSelected: List<FileInfo>? = null,
    val inputBrowser: List<FileInfo>? = null,
    val inputBrowseDir: String? = null,
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
//    val indexTaskRunning: Boolean = false,
    val taskRunning: Boolean = false,
    val taskModel: TaskModel? = null,
    val taskProgress: TaskProgress? = null,
    val taskLoadError: String? = null,
    val taskError: String? = null,

    val tableSummaryLoaded: Boolean = false,
    val tableSummaryLoading: Boolean = false,
    val tableSummary: TableSummary? = null,
    val tableSummaryError: String? = null/*,
    val indexTaskFinished: Boolean = false*/
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        val empty = ProcessState(
//            clientState = null,
//            tableSummaryTaskRunning = false,
//            filterTaskRunning = false
//        )

        fun tryCreate(clientState: SessionState): ReportState? {
//            console.log("^^^ tryCreate: $clientState")
            val mainLocation = tryMainLocation(clientState)
                ?: return null
//            console.log("^^^ tryCreate - got mainLocation: $mainLocation")

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

            if (! ReportConventions.isFilter(documentNotation)) {
                return null
            }

            return ObjectLocation(documentPath, NotationConventions.mainObjectPath)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isInitiating(): Boolean {
        return false
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
            .successful
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


    fun filterSpec(): FilterSpec {
        val filterDefinition = reportDefinition().attributeDefinitions[ReportConventions.filterAttributeName]!!
        return (filterDefinition as ValueAttributeDefinition).value as FilterSpec
    }


    fun pivotSpec(): PivotSpec {
        val pivotDefinition = reportDefinition().attributeDefinitions[ReportConventions.pivotAttributeName]!!
        return (pivotDefinition as ValueAttributeDefinition).value as PivotSpec
    }


    fun outputSpec(): OutputSpec {
        val pivotDefinition = reportDefinition().attributeDefinitions[ReportConventions.outputAttributeName]!!
        return (pivotDefinition as ValueAttributeDefinition).value as OutputSpec
    }


    fun inputAndCalculatedColumns(): List<String>? {
        if (columnListing == null) {
            return null
        }
        return columnListing + formulaSpec().formulas.keys
    }
}