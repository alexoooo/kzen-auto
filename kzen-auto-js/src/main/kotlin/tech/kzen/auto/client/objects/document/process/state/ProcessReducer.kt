package tech.kzen.auto.client.objects.document.process.state

import tech.kzen.auto.common.objects.document.filter.FilterConventions
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.reactive.TableSummary
import tech.kzen.auto.common.paradigm.reactive.TaskProgress
import tech.kzen.auto.common.paradigm.task.model.TaskState


object ProcessReducer {
    //-----------------------------------------------------------------------------------------------------------------
    fun reduce(
        state: ProcessState,
        action: ProcessAction
    ): ProcessState {
        return when (action) {
            //--------------------------------------------------
            InitiateProcessEffect -> state
            is ProcessRefreshSchedule -> state

            //--------------------------------------------------
            is ListInputsAction ->
                reduceListInputs(state, action)

            is ListColumnsAction ->
                reduceListColumns(state, action)

            is ProcessTaskAction ->
                reduceTask(state, action)

            is SummaryLookupAction ->
                reduceSummaryLookup(state, action)

            is FilterAction ->
                reduceFilter(state, action)

            //--------------------------------------------------
//            else ->
//                state
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceListInputs(
        state: ProcessState,
        action: ListInputsAction
    ): ProcessState {
        return when (action) {
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

            is ProcessTaskLookupResponse -> {
                val model = action.taskModel

                if (model == null) {
                    state.copy(
                        taskLoading = false,
                        taskLoaded = true,
                        taskModel = null,
                        taskProgress = null,
                        indexTaskRunning = false,
                        filterTaskRunning = false)
                }
                else {
                    val result = model.finalOrPartialResult()

                    val requestAction = model.request.parameters.get(FilterConventions.actionParameter)!!
                    val isIndexing = requestAction == FilterConventions.actionSummaryTask
                    val isRunning = model.state == TaskState.Running

                    when (result) {
                        is ExecutionSuccess -> {
                            val tableSummary = model.tableSummary()!!
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
            }

            is ProcessTaskRunRequest -> state.copy(
                taskStarting = true)

            is ProcessTaskRunResponse -> {
                val isRunning = action.taskModel.state == TaskState.Running
                val requestAction = action.taskModel.request.parameters.get(FilterConventions.actionParameter)!!
                val isIndexing = requestAction == FilterConventions.actionSummaryTask

                state.copy(
                    taskModel = action.taskModel,
                    tableSummary = action.taskModel.tableSummary(),
                    taskProgress = action.taskModel.taskProgress(),
                    indexTaskRunning = isRunning && isIndexing,
                    filterTaskRunning = isRunning && ! isIndexing,
                    taskStarting = false)
            }

            is ProcessTaskRefreshRequest ->
                state

            is ProcessTaskRefreshResponse -> {
                if (action.taskModel == null) {
                    state.copy(
                        taskModel = null,
                        taskProgress = null,
                        indexTaskRunning = false,
                        filterTaskRunning = false)
                }
                else {
                    val isRunning = action.taskModel.state == TaskState.Running
                    val requestAction = action.taskModel.request.parameters.get(FilterConventions.actionParameter)!!
                    val isIndexing = requestAction == FilterConventions.actionSummaryTask

                    state.copy(
                        taskModel = action.taskModel,
                        tableSummary = action.taskModel.tableSummary(),
                        taskProgress = action.taskModel.taskProgress(),
                        indexTaskRunning = isRunning && isIndexing,
                        filterTaskRunning = isRunning && ! isIndexing)
                }
            }
        }
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
    private fun reduceFilter(
        state: ProcessState,
        action: FilterAction
    ): ProcessState {
        return when (action) {
            is FilterAddRequest -> state.copy(
                filterAddLoading = true,
                filterAddError = null)

            FilterAddResponse -> state.copy(
                filterAddLoading = false)

            is FilterAddError -> state.copy(
                filterAddLoading = false,
                filterAddError = action.message)


            is FilterValueAddRequest -> state
            is FilterValueRemoveRequest -> state
            is FilterUpdateResult -> state

            is FilterRemoveRequest -> state
        }
    }
}