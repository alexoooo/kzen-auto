package tech.kzen.auto.client.objects.document.process.state

import tech.kzen.auto.common.objects.document.process.ProcessConventions
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.reactive.TableSummary
import tech.kzen.auto.common.paradigm.task.model.TaskState


object ProcessReducer {
    //-----------------------------------------------------------------------------------------------------------------
    fun reduce(
        state: ProcessState,
        action: SingularProcessAction
    ): ProcessState {
        return when (action) {
            //--------------------------------------------------
            is InitiateProcessAction ->
                reduceInitiate(state, action)

            is ProcessRefreshAction -> state

            //--------------------------------------------------
            is InputProcessAction ->
                reduceInputs(state, action)

            is ListColumnsAction ->
                reduceListColumns(state, action)

            is ProcessTaskAction ->
                reduceTask(state, action)

            is SummaryLookupAction ->
                reduceSummaryLookup(state, action)

            is OutputLookupAction ->
                reduceOutputLookup(state, action)

            is FilterAction ->
                reduceFilter(state, action)

            is PivotAction ->
                reducePivot(state, action)

            ProcessSaveAction ->
                state

            //--------------------------------------------------
//            else ->
//                state
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceInitiate(
        state: ProcessState,
        action: InitiateProcessAction
    ): ProcessState {
        return when (action) {
            InitiateProcessStart -> state.copy(
                initiating = true)

            InitiateProcessDone -> state.copy(
                initiating = false)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceInputs(
        state: ProcessState,
        action: InputProcessAction
    ): ProcessState {
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
        state: ProcessState,
        action: ListColumnsAction
    ): ProcessState {
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
        state: ProcessState,
        action: ProcessTaskAction
    ): ProcessState {
        return when (action) {
            ProcessTaskLookupRequest -> state.copy(
                taskLoading = true)

            is ProcessTaskLookupResponse ->
                reduceTaskLookupResponse(state, action)

            is ProcessTaskRunRequest -> state.copy(
                taskStarting = true,
                indexTaskFinished = false)

            is ProcessTaskRunResponse ->
                reduceTaskRunResponse(state, action)

            is ProcessTaskRefreshRequest ->
                state

            is ProcessTaskRefreshResponse ->
                reduceTaskRefreshResponse(state, action)

            is ProcessTaskStopRequest -> state.copy(
                taskStopping = true)

            is ProcessTaskStopResponse ->
                reduceTaskStopResponse(state, action)
        }
    }


    private fun reduceTaskLookupResponse(
        state: ProcessState,
        action: ProcessTaskLookupResponse
    ): ProcessState {
        val model = action.taskModel
            ?: return state.copy(
                taskLoading = false,
                taskLoaded = true,
                taskModel = null,
                taskProgress = null,
                indexTaskRunning = false,
                filterTaskRunning = false)

        val result = model.finalOrPartialResult()

        val requestAction = model.requestAction()
        val isIndexing = requestAction == ProcessConventions.actionSummaryTask
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
                    indexTaskRunning = isRunning && isIndexing,
                    filterTaskRunning = isRunning && ! isIndexing,
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
        state: ProcessState,
        action: ProcessTaskRunResponse
    ): ProcessState {
        val isRunning = action.taskModel.state == TaskState.Running
        val requestAction = action.taskModel.requestAction()
        val isIndexing = requestAction == ProcessConventions.actionSummaryTask

        val tableSummary =
            if (isIndexing) {
                TableSummary.fromTaskModel(action.taskModel)
            }
            else {
                state.tableSummary
            }

        return state.copy(
            taskModel = action.taskModel,
            tableSummary = tableSummary,
            taskProgress = action.taskModel.taskProgress(),
            indexTaskRunning = isRunning && isIndexing,
            filterTaskRunning = isRunning && ! isIndexing,
            taskError = action.taskModel.errorMessage(),
            taskStarting = false)
    }


    private fun reduceTaskRefreshResponse(
        state: ProcessState,
        action: ProcessTaskRefreshResponse
    ): ProcessState {
        if (action.taskModel == null) {
            val indexTaskFinished = state.indexTaskRunning
            return state.copy(
                taskModel = null,
                taskProgress = null,
                indexTaskRunning = false,
                filterTaskRunning = false,
                indexTaskFinished = indexTaskFinished)
        }

        val isRunning = action.taskModel.state == TaskState.Running
        val requestAction = action.taskModel.requestAction()
        val isIndexing = requestAction == ProcessConventions.actionSummaryTask

        val tableSummary =
            if (isIndexing) {
                TableSummary.fromTaskModel(action.taskModel)
            }
            else {
                state.tableSummary
            }

        val taskProcess = action.taskModel.taskProgress()
        val indexTaskFinished =
            isIndexing && action.taskModel.state == TaskState.Done && taskProcess == null

        return state.copy(
            taskModel = action.taskModel,
            tableSummary = tableSummary,
            taskProgress = taskProcess,
            indexTaskRunning = isRunning && isIndexing,
            filterTaskRunning = isRunning && ! isIndexing,
            indexTaskFinished = indexTaskFinished,
            taskError = action.taskModel.errorMessage())
    }


    private fun reduceTaskStopResponse(
        state: ProcessState,
        action: ProcessTaskStopResponse
    ): ProcessState {
        val requestAction = action.taskModel.requestAction()
        val isIndexing = requestAction == ProcessConventions.actionSummaryTask

        val tableSummary =
            if (isIndexing) {
                TableSummary.fromTaskModel(action.taskModel)
            }
            else {
                state.tableSummary
            }

        return state.copy(
            taskStopping = false,
            tableSummary = tableSummary,
            indexTaskRunning = false,
            filterTaskRunning = false,
            taskProgress = null,
            indexTaskFinished = false)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceSummaryLookup(
        state: ProcessState,
        action: SummaryLookupAction
    ): ProcessState {
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
                    tableSummary = action.tableSummary,
                    taskProgress = action.taskProgress)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceOutputLookup(
        state: ProcessState,
        action: OutputLookupAction
    ): ProcessState {
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
    private fun reduceFilter(
        state: ProcessState,
        action: FilterAction
    ): ProcessState {
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
        state: ProcessState,
        action: PivotAction
    ): ProcessState {
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