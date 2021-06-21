package tech.kzen.auto.client.objects.document.report.state

import tech.kzen.auto.common.objects.document.report.progress.ReportProgress
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.task.model.TaskState


object ReportReducer {
    //-----------------------------------------------------------------------------------------------------------------
    fun reduce(
        state: ReportState,
        action: SingularReportAction
    ): ReportState {
        return when (action) {
            //--------------------------------------------------
            is InitiateReport ->
//                reduceInitiate(state, action)
                state

            is ReportRefreshAction -> state

            //--------------------------------------------------
            is InputReportAction ->
                reduceInputs(state, action)

            is ListColumnsAction ->
                reduceListColumns(state, action)

            is ReportTaskAction ->
                reduceTask(state, action)

            is SummaryLookupAction ->
                reduceSummaryLookup(state, action)

            is OutputAction ->
                reduceOutputLookup(state, action)

            is FormulaAction ->
                reduceFormula(state, action)

            is FilterAction ->
                reduceFilter(state, action)

            is AnalysisAction ->
                reducePivot(state, action)

            ReportSaveAction ->
                state

            ReportResetAction ->
                state

            is ReportResetResult -> state.copy(
                reportProgress = null,
                taskError = null,
                tableSummaryError = null,
                outputError = null)

            is ReportPluginAction ->
                state
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun reduceInitiate(
//        state: ReportState,
//        action: InitiateReport
//    ): ReportState {
////        console.log("#@##!#! reduceInitiate - ${action::class.simpleName}")
//        return when (action) {
//            InitiateReport -> state.copy(
//                initiating = true)
//
////            InitiateReportDone -> state.copy(
////                initiating = false)
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceInputs(
        state: ReportState,
        action: InputReportAction
    ): ReportState {
        return when (action) {
            ListInputsBrowserRequest,
            ListInputsSelectedRequest,
            is ListInputsBrowserNavigate,
            is InputsSelectionAddRequest,
            is InputsSelectionRemoveRequest,
            is InputsSelectionDataTypeRequest,
            is InputsSelectionGroupByRequest,
            is InputsSelectionFormatRequest,
            is InputsSelectionMultiFormatRequest,
            is InputsBrowserFilterRequest -> state.copy(
                inputLoading = true,
                inputError = null)

            is ListInputsSelectedResult -> state.copy(
                inputSelection = action.inputSelectionInfo,
                inputLoaded = true,
                inputLoading = false,
                columnListing = null,
                columnListingError = null)

            is ListInputsBrowserResult -> state.copy(
                inputBrowser = action.inputBrowserInfo.files,
                inputBrowseDir = action.inputBrowserInfo.browseDir,
                inputLoaded = true,
                inputLoading = false)

            is ListInputsError -> state.copy(
                inputError = action.message,
                inputLoaded = true,
                inputLoading = false)

            EmptyInputSelection -> state.copy(
                columnListingLoading = false,
                formulaMessages = mapOf(),
                outputInfo = null)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceListColumns(
        state: ReportState,
        action: ListColumnsAction
    ): ReportState {
        return when (action) {
            ListColumnsRequest -> state.copy(
                columnListingLoading = true,
                columnListing = null,
                columnListingError = null)

            is ListColumnsResponse -> state.copy(
                columnListing = action.columnListing,
                columnListingLoaded = true,
                columnListingLoading = false)

            is ListColumnsError -> state.copy(
                columnListingError = action.message,
                columnListingLoaded = true,
                columnListingLoading = false)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceTask(
        state: ReportState,
        action: ReportTaskAction
    ): ReportState {
        return when (action) {
            ReportTaskLookupRequest -> state.copy(
                taskLoading = true)

            is ReportTaskLookupResponse ->
                reduceTaskLookupResponse(state, action)

            is ReportTaskRunRequest -> state.copy(
                taskStarting = true)

            is ReportTaskRunResponse ->
                reduceTaskRunResponse(state, action)

            is ReportTaskRefreshRequest ->
                state

            is ReportTaskRefreshResponse ->
                reduceTaskRefreshResponse(state, action)

            is ReportTaskStopRequest -> state.copy(
                taskStopping = true)

            is ReportTaskStopResponse ->
                reduceTaskStopResponse(state)

            ReportProgressReset -> state.copy(
                reportProgress =  null)
        }
    }


    private fun reduceTaskLookupResponse(
        state: ReportState,
        action: ReportTaskLookupResponse
    ): ReportState {
        val model = action.taskModel
            ?: return state.copy(
                taskLoading = false,
                taskLoaded = true,
                taskModel = null,
                reportProgress = null,
                taskRunning = false)

        val result = model.finalOrPartialResult()
        val isRunning = model.state == TaskState.Running

        return when (result) {
            is ExecutionSuccess -> {
                val tableSummary =
                    TableSummary.fromExecutionSuccess(result)
                    ?: state.tableSummary

                val taskProgress = ReportProgress.fromTaskProgress(
                    model.taskProgress()!!)

                state.copy(
                    taskLoading = false,
                    taskLoaded = true,
                    taskModel = model,
                    reportProgress = taskProgress,
                    taskRunning = isRunning,
                    tableSummary = tableSummary,
                    tableSummaryLoaded = true,
                    tableSummaryLoading = false)
            }

            is ExecutionFailure -> {
                state.copy(
                    taskLoading = false,
                    taskLoaded = true,
                    taskModel = model,
                    taskLoadError = result.errorMessage)
            }
        }
    }


    private fun reduceTaskRunResponse(
        state: ReportState,
        action: ReportTaskRunResponse
    ): ReportState {
        val isRunning = action.taskModel.state == TaskState.Running

        val tableSummary = state.tableSummary

        val taskProgress = action.taskModel.taskProgress()
            ?.let { ReportProgress.fromTaskProgress(it) }

        return state.copy(
            taskModel = action.taskModel,
            tableSummary = tableSummary,
            reportProgress = taskProgress,
            taskRunning = isRunning,
            taskError = action.taskModel.errorMessage(),
            taskStarting = false
        )
    }


    private fun reduceTaskRefreshResponse(
        state: ReportState,
        action: ReportTaskRefreshResponse
    ): ReportState {
        if (action.taskModel == null) {
            return state.copy(
                taskModel = null,
                reportProgress = null,
                taskRunning = false)
        }

        val isRunning = action.taskModel.state == TaskState.Running
        val tableSummary = state.tableSummary

        val taskProgress = action.taskModel.taskProgress()
            ?.let { ReportProgress.fromTaskProgress(it) }

//        console.log("^^^^ taskProgress?.outputCount - ${taskProgress?.outputCount}")

        return state.copy(
            taskModel = action.taskModel,
            tableSummary = tableSummary,
            reportProgress = taskProgress,
            taskRunning = isRunning,
            taskError = action.taskModel.errorMessage()
        )
    }


    private fun reduceTaskStopResponse(
        state: ReportState
    ): ReportState {
        val tableSummary = state.tableSummary

        return state.copy(
            taskStopping = false,
            tableSummary = tableSummary,
            taskRunning = false,
            reportProgress = null)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceSummaryLookup(
        state: ReportState,
        action: SummaryLookupAction
    ): ReportState {
        return when (action) {
            SummaryLookupRequest -> state.copy(
                tableSummaryLoading = true)

            is SummaryLookupError -> state.copy(
                tableSummaryLoading = false,
                tableSummaryError = action.errorMessage)

            is SummaryLookupResult -> state.copy(
                    tableSummaryLoading = false,
                    tableSummaryLoaded = true,
                    tableSummaryError = null,
                    tableSummary = action.tableSummary)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceOutputLookup(
        state: ReportState,
        action: OutputAction
    ): ReportState {
        return when (action) {
            is OutputLookupRequest -> state.copy(
                outputLoading = true)

            is OutputErrorResult -> state.copy(
                outputLoaded = true,
                outputLoading = false,
                outputError = action.errorMessage)

            is OutputLookupResult -> state.copy(
                outputLoaded = true,
                outputLoading = false,
                outputInfo = action.outputInfo,
                outputError = null)

            is OutputChangeTypeRequest -> state
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceFormula(
        state: ReportState,
        action: FormulaAction
    ): ReportState {
        return when (action) {
            is FormulaUpdateRequest -> state.copy(
                formulaLoading = true,
                formulaError = null)

            is FormulaUpdateResult -> state.copy(
                formulaLoading = false,
                formulaError = action.errorMessage)

            FormulaValidationRequest -> state.copy(
                formulaLoading = true,
                formulaError = null)

            is FormulaValidationResult -> state.copy(
                formulaLoading = false,
                formulaMessages = action.messages,
                formulaError = action.errorMessage)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceFilter(
        state: ReportState,
        action: FilterAction
    ): ReportState {
        return when (action) {
            is FilterUpdateRequest -> state.copy(
                filterLoading = true,
                filterError = null)

            is FilterUpdateResult -> state.copy(
                filterLoading = false,
                filterError = action.errorMessage)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reducePivot(
        state: ReportState,
        action: AnalysisAction
    ): ReportState {
        return when (action) {
            is AnalysisUpdateRequest -> state.copy(
                pivotLoading = true,
                pivotError = null)

            is AnalysisUpdateResult -> state.copy(
                pivotLoading = false,
                pivotError = action.errorMessage)
        }
    }
}