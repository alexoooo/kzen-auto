package tech.kzen.auto.client.objects.document.report.state

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
            is InitiateReportAction ->
                reduceInitiate(state, action)

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
    private fun reduceInitiate(
        state: ReportState,
        action: InitiateReportAction
    ): ReportState {
        return when (action) {
            InitiateReportStart -> state.copy(
                initiating = true)

            InitiateReportDone -> state.copy(
                initiating = false)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceInputs(
        state: ReportState,
        action: InputReportAction
    ): ReportState {
        return when (action) {
            InputsUpdatedRequest -> state

            ListInputsRequest -> state.copy(
                fileListingLoading = true,
                fileListing = null,
                fileListingError = null)

            is ListInputsResult -> state.copy(
                fileListing = action.fileListing,
                fileListingLoaded = true,
                fileListingLoading = false,
                columnListing = null,
                columnListingError = null)

            is ListInputsError -> state.copy(
                fileListingError = action.message,
                fileListingLoaded = true,
                fileListingLoading = false)
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
                taskStarting = true/*,
                indexTaskFinished = false*/)

            is ReportTaskRunResponse ->
                reduceTaskRunResponse(state, action)

            is ReportTaskRefreshRequest ->
                state

            is ReportTaskRefreshResponse ->
                reduceTaskRefreshResponse(state, action)

            is ReportTaskStopRequest -> state.copy(
                taskStopping = true)

            is ReportTaskStopResponse ->
                reduceTaskStopResponse(state, action)
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
//                indexTaskRunning = false,
                taskRunning = false)

        val result = model.finalOrPartialResult()

//        val requestAction = model.requestAction()
//        val isIndexing = requestAction == ProcessConventions.actionSummaryTask
//        val isIndexing = false
        val isRunning = model.state == TaskState.Running

        return when (result) {
            is ExecutionSuccess -> {
                val tableSummary =
                    TableSummary.fromExecutionSuccess(result)
                    ?: state.tableSummary

                val tableSummaryProgress = model.taskProgress()!!

                state.copy(
                    taskLoading = false,
                    taskLoaded = true,
                    taskModel = model,
                    taskProgress = tableSummaryProgress,
//                    indexTaskRunning = isRunning && isIndexing,
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
//        val requestAction = action.taskModel.requestAction()
//        val isIndexing = requestAction == ProcessConventions.actionSummaryTask

        val tableSummary =
//            if (isIndexing) {
//                TableSummary.fromTaskModel(action.taskModel)
//            }
//            else {
                state.tableSummary
//            }

        return state.copy(
            taskModel = action.taskModel,
            tableSummary = tableSummary,
            taskProgress = action.taskModel.taskProgress(),
//            indexTaskRunning = isRunning && isIndexing,
//            indexTaskRunning = false,
//            filterTaskRunning = isRunning && ! isIndexing,
            taskRunning = isRunning,
            taskError = action.taskModel.errorMessage(),
            taskStarting = false)
    }


    private fun reduceTaskRefreshResponse(
        state: ReportState,
        action: ReportTaskRefreshResponse
    ): ReportState {
        if (action.taskModel == null) {
//            val indexTaskFinished = state.indexTaskRunning
            return state.copy(
                taskModel = null,
                taskProgress = null,
//                indexTaskRunning = false,
                taskRunning = false/*,
                indexTaskFinished = indexTaskFinished*/)
        }

        val isRunning = action.taskModel.state == TaskState.Running
//        val requestAction = action.taskModel.requestAction()
//        val isIndexing = requestAction == ProcessConventions.actionSummaryTask
        val isIndexing = false

        val tableSummary =
//            if (isIndexing) {
//                TableSummary.fromTaskModel(action.taskModel)
//            }
//            else {
                state.tableSummary
//            }

        val taskProcess = action.taskModel.taskProgress()
        val indexTaskFinished =
            isIndexing && action.taskModel.state == TaskState.FinishedOrFailed && taskProcess == null

        return state.copy(
            taskModel = action.taskModel,
            tableSummary = tableSummary,
            taskProgress = taskProcess,
//            indexTaskRunning = isRunning && isIndexing,
//            indexTaskRunning = false,
//            filterTaskRunning = isRunning && ! isIndexing,
            taskRunning = isRunning,
//            indexTaskFinished = indexTaskFinished,
            taskError = action.taskModel.errorMessage())
    }


    private fun reduceTaskStopResponse(
        state: ReportState,
        action: ReportTaskStopResponse
    ): ReportState {
//        val requestAction = action.taskModel.requestAction()
//        val isIndexing = requestAction == ProcessConventions.actionSummaryTask
        val isIndexing = false

        val tableSummary =
//            if (isIndexing) {
//                TableSummary.fromTaskModel(action.taskModel)
//            }
//            else {
                state.tableSummary
//            }

        return state.copy(
            taskStopping = false,
            tableSummary = tableSummary,
//            indexTaskRunning = false,
            taskRunning = false,
            taskProgress = null/*,
            indexTaskFinished = false*/)
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
                    tableSummary = action.tableSummary/*,
                    taskProgress = action.taskProgress*/)
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
                outputLoading = false,
                outputError = action.errorMessage)

            is OutputLookupResult -> state.copy(
                outputLoading = false,
                outputInfo = action.outputInfo,
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