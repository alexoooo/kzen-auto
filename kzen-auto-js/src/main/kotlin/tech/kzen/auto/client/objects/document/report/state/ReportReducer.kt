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

            is OutputLookupAction ->
                reduceOutputLookup(state, action)

            is FormulaAction ->
                reduceFormula(state, action)

            is FilterAction ->
                reduceFilter(state, action)

            is PivotAction ->
                reducePivot(state, action)

            ReportSaveAction -> state
            ReportResetAction -> state
            is ReportResetResult -> state

            //--------------------------------------------------
//            else ->
//                state
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
//            InputsUpdatedRequest,
            ListInputsBrowserRequest,
            ListInputsSelectedRequest,
            is ListInputsBrowserNavigate,
            is InputsSelectionAddRequest,
            is InputsSelectionRemoveRequest,
            is InputsBrowserFilterRequest -> state.copy(
                inputLoading = true,
                inputError = null)

            is ListInputsSelectedResult -> state.copy(
                inputSelected = action.inputInfo.files,
                inputBrowseDir = action.inputInfo.browseDir,
                inputLoaded = true,
                inputLoading = false,
                columnListing = null,
                columnListingError = null)

            is ListInputsBrowserResult -> state.copy(
                inputBrowser = action.inputInfo.files,
                inputBrowseDir = action.inputInfo.browseDir,
                inputLoaded = true,
                inputLoading = false,
                columnListing = null,
                columnListingError = null)

            is ListInputsError -> state.copy(
                inputError = action.message,
                inputLoaded = true,
                inputLoading = false)
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
                taskProgress = null,
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
                    taskProgress = taskProgress,
                    taskRunning = isRunning,
                    tableSummary = tableSummary,
                    tableSummaryLoaded = true,
                    tableSummaryLoading = false,
                    outputCount = taskProgress.outputCount.coerceAtLeast(state.outputCount ?: 0))
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
            taskProgress = taskProgress,
            taskRunning = isRunning,
            taskError = action.taskModel.errorMessage(),
            taskStarting = false,
            outputCount = (taskProgress?.outputCount ?: 0).coerceAtLeast(state.outputCount ?: 0)
        )
    }


    private fun reduceTaskRefreshResponse(
        state: ReportState,
        action: ReportTaskRefreshResponse
    ): ReportState {
        if (action.taskModel == null) {
            return state.copy(
                taskModel = null,
                taskProgress = null,
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
            taskProgress = taskProgress,
            taskRunning = isRunning,
            taskError = action.taskModel.errorMessage(),
            outputCount = (taskProgress?.outputCount ?: 0).coerceAtLeast(state.outputCount ?: 0)
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
            taskProgress = null)
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
        action: OutputLookupAction
    ): ReportState {
        return when (action) {
            is OutputLookupRequest -> state.copy(
                outputLoading = true)

            is OutputLookupError -> state.copy(
                outputLoaded = true,
                outputLoading = false,
                outputError = action.errorMessage)

            is OutputLookupResult -> state.copy(
                outputLoaded = true,
                outputLoading = false,
                outputInfo = action.outputInfo,
                outputCount = (state.outputCount ?: 0).coerceAtLeast(action.outputInfo.rowCount),
                outputError = null)
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
        action: PivotAction
    ): ReportState {
        return when (action) {
            is PivotUpdateRequest -> state.copy(
                pivotLoading = true,
                pivotError = null)

            is PivotUpdateResult -> state.copy(
                pivotLoading = false,
                pivotError = action.errorMessage)
        }
    }
}