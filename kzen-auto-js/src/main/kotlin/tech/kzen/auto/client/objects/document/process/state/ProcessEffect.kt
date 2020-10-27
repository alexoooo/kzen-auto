package tech.kzen.auto.client.objects.document.process.state

import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.common.objects.document.process.*
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.paradigm.reactive.TableSummary
import tech.kzen.auto.common.paradigm.reactive.TaskProgress
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskState
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphSuccess


object ProcessEffect {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun effect(
        state: ProcessState,
//        prevState: ProcessState,
        action: SingularProcessAction
    ): ProcessAction? {
//        console.log("ProcessEffect action: ", action)

        if (action == InitiateProcessStart ||
                action == InputsUpdatedRequest
        ) {
            return CompoundProcessAction(ListInputsRequest, OutputLookupRequest)
        }

        return when (action) {
            OutputLookupRequest ->
                lookupOutput(state)


//            InputsUpdatedRequest ->
//                ListInputsRequest

            ListInputsRequest ->
                loadFileListing(state)

            is ListInputsResponse ->
                ListColumnsRequest


            ListColumnsRequest ->
                loadColumnListing(state)


            is ListColumnsResponse ->
                if (action.columnListing.isNotEmpty()) {
                    ProcessTaskLookupRequest
                }
                else {
                    null
                }


            ProcessTaskLookupRequest ->
                loadTask(state)


            is ProcessTaskLookupResponse ->
                taskLoaded(action)


            SummaryLookupRequest ->
                lookupSummary(state)


            is ProcessTaskRunRequest ->
                runTask(state, action.type)

            is ProcessTaskRunResponse ->
                taskRunning(action)

            is ProcessTaskRefreshRequest ->
                refreshTask(action.taskId)

            is ProcessTaskRefreshResponse ->
                refreshTaskLoop(action)

            is ProcessTaskStopRequest ->
                stopTask(action)

            is ProcessTaskStopResponse ->
                taskStopped(action)


            is FilterAddRequest ->
                submitFilterAdd(state, action.columnName)

            is FilterRemoveRequest ->
                submitFilterRemove(state, action.columnName)

            is FilterValueAddRequest ->
                submitFilterValueAdd(state, action.columnName, action.filterValue)

            is FilterValueRemoveRequest ->
                submitFilterValueRemove(state, action.columnName, action.filterValue)

            is FilterTypeChangeRequest ->
                submitFilterTypeChange(state, action.columnName, action.filterType)


            is PivotRowAddRequest ->
                submitPivotRowAdd(state, action.columnName)

            is PivotRowRemoveRequest ->
                submitPivotRowRemove(state, action.columnName)

            is PivotRowClearRequest ->
                submitPivotRowClear(state)

            is PivotValueAddRequest ->
                submitPivotValueAdd(state, action.columnName)

            is PivotValueRemoveRequest ->
                submitPivotValueRemove(state, action.columnName)

            is PivotValueTypeAddRequest ->
                submitPivotValueTypeAdd(state, action.columnName, action.valueType)

            is PivotValueTypeRemoveRequest ->
                submitPivotValueTypeRemove(state, action.columnName, action.valueType)

            is ProcessUpdateResult ->
                if (action.errorMessage == null) {
                    OutputLookupRequest
                }
                else {
                    null
                }

            else -> null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun loadFileListing(
        state: ProcessState
    ): ProcessAction {
        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            ProcessConventions.actionParameter to ProcessConventions.actionListFiles)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as List<String>

                ListInputsResult(resultValue)
            }

            is ExecutionFailure -> {
                ListInputsError(result.errorMessage)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun loadColumnListing(
        state: ProcessState
    ): ProcessAction? {
        if (state.fileListing.isNullOrEmpty()) {
            return null
        }

        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            ProcessConventions.actionParameter to ProcessConventions.actionListColumns)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as List<String>

                ListColumnsResponse(resultValue)
            }

            is ExecutionFailure -> {
                ListColumnsError(result.errorMessage)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun loadTask(
        state: ProcessState
    ): ProcessTaskLookupResponse {
        val activeTasks = ClientContext.clientRestTaskRepository.lookupActive(state.mainLocation)

        if (activeTasks.isEmpty()) {
            return ProcessTaskLookupResponse(null)
        }

        val taskId = activeTasks.single()
        val model = ClientContext.clientRestTaskRepository.query(taskId)!!

        return ProcessTaskLookupResponse(model)
    }


    private fun taskLoaded(
        action: ProcessTaskLookupResponse
    ): ProcessAction? {
        val taskModel = action.taskModel
            ?: return SummaryLookupRequest

        val tableSummary =
            (taskModel.finalOrPartialResult() as? ExecutionSuccess)?.let {
                TableSummary.fromExecutionSuccess(it)
            }

        val firstAction =
            if (tableSummary == null) {
                SummaryLookupRequest
            }
            else {
                null
            }

        val secondAction =
            if (taskModel.state == TaskState.Running) {
                ProcessRefreshSchedule(
                    ProcessTaskRefreshRequest(action.taskModel.taskId))
            }
            else {
                null
            }

        return CompoundProcessAction.of(firstAction, secondAction)
    }


    private suspend fun refreshTask(
        taskId: TaskId
    ): ProcessAction {
        val model = ClientContext.clientRestTaskRepository.query(taskId)
        return ProcessTaskRefreshResponse(model)
    }


    private fun refreshTaskLoop(
        action: ProcessTaskRefreshResponse
    ): ProcessAction? {
        val taskModel = action.taskModel
            ?: return null

        if (taskModel.state == TaskState.Running) {
            return ProcessRefreshSchedule(
                ProcessTaskRefreshRequest(action.taskModel.taskId))
        }

        val requestAction = taskModel.requestAction()
        val isFiltering = requestAction == ProcessConventions.actionFilterTask

        if (isFiltering) {
            return OutputLookupRequest
        }

        return null
    }


    private suspend fun stopTask(
        action: ProcessTaskStopRequest
    ): ProcessTaskStopResponse? {
        val taskModel = ClientContext.clientRestTaskRepository.cancel(action.taskId)
            ?: return null

        return ProcessTaskStopResponse(taskModel)
    }


    private fun taskStopped(
        action: ProcessTaskStopResponse
    ): ProcessAction? {
        val requestAction = action.taskModel.requestAction()
        val isFiltering = requestAction == ProcessConventions.actionFilterTask

        return if (isFiltering) {
            CompoundProcessAction(
                ProcessRefreshCancel, OutputLookupRequest)
        }
        else {
            ProcessRefreshCancel
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun lookupSummary(
        state: ProcessState
    ): SummaryLookupAction {
        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            ProcessConventions.actionParameter to ProcessConventions.actionSummaryLookup)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, Map<String, Any>>
                val tableSummary = TableSummary.fromCollection(resultValue)

                @Suppress("UNCHECKED_CAST")
                val resultDetail = result.detail.get() as Map<String, String>
                val summaryProgress = TaskProgress.fromCollection(resultDetail)

                SummaryLookupResult(
                    tableSummary, summaryProgress)
            }

            is ExecutionFailure -> {
                SummaryLookupError(result.errorMessage)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun lookupOutput(
        state: ProcessState
    ): ProcessAction? {
        if (state.columnListing.isNullOrEmpty()) {
            return null
        }

        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            ProcessConventions.actionParameter to ProcessConventions.actionLookupOutput)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, Any?>
                val outputInfo = OutputInfo.fromCollection(resultValue)

                OutputLookupResult(outputInfo)
            }

            is ExecutionFailure -> {
                OutputLookupError(result.errorMessage)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun runTask(
        state: ProcessState,
        type: ProcessTaskType
    ): ProcessAction {
        val action = when (type) {
            ProcessTaskType.Index ->
                ProcessConventions.actionSummaryTask

            ProcessTaskType.Filter ->
                ProcessConventions.actionFilterTask
        }

        val result = ClientContext.clientRestTaskRepository.submit(
            state.mainLocation,
            DetachedRequest(
                RequestParams.of(
                    ProcessConventions.actionParameter to action),
                null))

        return ProcessTaskRunResponse(result)
    }


    private fun taskRunning(
        action: ProcessTaskRunResponse
    ): ProcessAction? {
        if (action.taskModel.state != TaskState.Running) {
            return null
        }

        return ProcessRefreshSchedule(
            ProcessTaskRefreshRequest(action.taskModel.taskId))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun submitFilterAdd(
        state: ProcessState,
        columnName: String
    ): ProcessAction {
        val command = FilterSpec.addCommand(state.mainLocation, columnName)

        val result = ClientContext.mirroredGraphStore
            .apply(command)

        return when (result) {
            is MirroredGraphError ->
                FilterUpdateResult(
                    result.error.message ?: "Failed: ${result.remote}")

            is MirroredGraphSuccess ->
                FilterUpdateResult(null)
        }
    }


    private suspend fun submitFilterRemove(
        state: ProcessState,
        columnName: String
    ): ProcessAction {
        val command = FilterSpec.removeCommand(state.mainLocation, columnName)

        val result = ClientContext.mirroredGraphStore
            .apply(command)

        return when (result) {
            is MirroredGraphError ->
                FilterUpdateResult(
                    result.error.message ?: "Failed: ${result.remote}")

            is MirroredGraphSuccess ->
                FilterUpdateResult(null)
        }
    }


    private suspend fun submitFilterValueAdd(
        state: ProcessState,
        columnName: String,
        filterValue: String
    ): ProcessAction {
        val command = FilterSpec.addValueCommand(
            state.mainLocation, columnName, filterValue)

        val result = ClientContext.mirroredGraphStore.apply(command)

        val errorMessage =
            (result as? MirroredGraphError)?.error?.message

        return FilterUpdateResult(errorMessage)
    }


    private suspend fun submitFilterTypeChange(
        state: ProcessState,
        columnName: String,
        filterType: ColumnFilterType
    ): ProcessAction {
        val command = FilterSpec.updateTypeCommand(
            state.mainLocation, columnName, filterType)

        val result = ClientContext.mirroredGraphStore.apply(command)

        val errorMessage =
            (result as? MirroredGraphError)?.error?.message

        return FilterUpdateResult(errorMessage)
    }


    private suspend fun submitFilterValueRemove(
        state: ProcessState,
        columnName: String,
        filterValue: String
    ): ProcessAction {
        val command = FilterSpec.removeValueCommand(
            state.mainLocation, columnName, filterValue)

        val result = ClientContext.mirroredGraphStore.apply(command)

        val errorMessage =
            (result as? MirroredGraphError)?.error?.message

        return FilterUpdateResult(errorMessage)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun submitPivotRowAdd(state: ProcessState, columnName: String): ProcessAction {
        return submitPivotUpdate(PivotSpec.addRowCommand(
            state.mainLocation, columnName))
    }


    private suspend fun submitPivotRowRemove(state: ProcessState, columnName: String): ProcessAction {
        return submitPivotUpdate(PivotSpec.removeRowCommand(
            state.mainLocation, columnName))
    }


    private suspend fun submitPivotRowClear(state: ProcessState): ProcessAction {
        return submitPivotUpdate(PivotSpec.clearRowCommand(
            state.mainLocation))
    }


    private suspend fun submitPivotValueAdd(state: ProcessState, columnName: String): ProcessAction {
        return submitPivotUpdate(PivotSpec.addValueCommand(
            state.mainLocation, columnName))
    }


    private suspend fun submitPivotValueRemove(state: ProcessState, columnName: String): ProcessAction {
        return submitPivotUpdate(PivotSpec.removeValueCommand(
            state.mainLocation, columnName))
    }


    private suspend fun submitPivotValueTypeAdd(
        state: ProcessState, columnName: String, valueType: PivotValueType
    ): ProcessAction {
        return submitPivotUpdate(PivotSpec.addValueTypeCommand(
            state.mainLocation, columnName, valueType))
    }


    private suspend fun submitPivotValueTypeRemove(
        state: ProcessState, columnName: String, valueType: PivotValueType
    ): ProcessAction {
        return submitPivotUpdate(PivotSpec.removeValueTypeCommand(
            state.mainLocation, columnName, valueType))
    }


    private suspend fun submitPivotUpdate(
        pivotUpdateCommand: NotationCommand
    ): ProcessAction {
        val result = ClientContext.mirroredGraphStore.apply(pivotUpdateCommand)

        val errorMessage =
            (result as? MirroredGraphError)?.error?.message

        return PivotUpdateResult(errorMessage)
    }
}