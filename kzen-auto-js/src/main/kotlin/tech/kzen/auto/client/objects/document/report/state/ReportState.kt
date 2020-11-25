package tech.kzen.auto.client.objects.document.report.state

import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.auto.common.paradigm.task.model.TaskProgress
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.notation.NotationConventions


data class ReportState(
    val clientState: SessionState,
    val mainLocation: ObjectLocation,

    val initiating: Boolean = false,

    val fileListingLoaded: Boolean = false,
    val fileListingLoading: Boolean = false,
    val fileListing: List<String>? = null,
    val fileListingError: String? = null,

    val columnListingLoaded: Boolean = false,
    val columnListingLoading: Boolean = false,
    val columnListing: List<String>? = null,
    val columnListingError: String? = null,

    val filterLoading: Boolean = false,
    val filterError: String? = null,

    val pivotLoading: Boolean = false,
    val pivotError: String? = null,

    val outputLoaded: Boolean = false,
    val outputLoading: Boolean = false,
    val outputInfo: OutputInfo? = null,
    val outputError: String? = null,
//    val outputPreviewStart: Long = 0,
//    val outputPreviewCount: Int = OutputPreview.defaultRowCount,

    val taskLoaded: Boolean = false,
    val taskLoading: Boolean = false,
    val taskStarting: Boolean = false,
    val taskStopping: Boolean = false,
//    val indexTaskRunning: Boolean = false,
    val filterTaskRunning: Boolean = false,
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
    fun isTaskRunning(): Boolean {
//        return indexTaskRunning || filterTaskRunning
        return filterTaskRunning
    }


//    fun isInitialLoading(): Boolean {
//        return fileListingLoading || columnListingLoading || taskLoading || outputLoading
//    }


    fun isLoadingError(): Boolean {
        return columnListingError != null ||
                fileListingError != null ||
                taskLoadError != null ||
                tableSummaryError != null
    }


    fun nextErrorMessage(): String? {
        return columnListingError
            ?: fileListingError
            ?: taskLoadError
            ?: taskError
            ?: tableSummaryError
    }
}