package tech.kzen.auto.client.objects.document.process.state

import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.common.objects.document.process.ColumnCriteriaType
import tech.kzen.auto.common.objects.document.process.CriteriaSpec
import tech.kzen.auto.common.objects.document.process.FilterConventions
import tech.kzen.auto.common.objects.document.process.OutputInfo
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.paradigm.reactive.TableSummary
import tech.kzen.auto.common.paradigm.reactive.TaskProgress
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskState
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionIndex
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertMapEntryInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveInAttributeCommand
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphSuccess
import tech.kzen.lib.platform.collect.persistentListOf


object ProcessEffect {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun effect(
        state: ProcessState,
//        prevState: ProcessState,
        action: SingularProcessAction
    ): ProcessAction? {
//        console.log("ProcessEffect action: ", action)

        if (action == InitiateProcessStart) {
            return CompoundProcessAction(ListInputsRequest, OutputLookupRequest)
        }

        return when (action) {
            OutputLookupRequest ->
                lookupOutput(state)

            ListInputsRequest ->
                loadFileListing(state)

            is ListInputsResponse ->
                ListColumnsRequest


            ListColumnsRequest ->
                loadColumnListing(state)


            is ListColumnsResponse ->
                ProcessTaskLookupRequest


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
                submitFilterTypeChange(state, action.columnName, action.criteriaType)


            else -> null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun loadFileListing(
        state: ProcessState
    ): ProcessAction {
        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            FilterConventions.actionParameter to FilterConventions.actionListFiles)

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
    ): ProcessAction {
        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            FilterConventions.actionParameter to FilterConventions.actionListColumns)

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

        if (taskModel.state == TaskState.Running) {
            return ProcessRefreshSchedule(
                ProcessTaskRefreshRequest(action.taskModel.taskId))
        }

        return null
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
        val isFiltering = requestAction == FilterConventions.actionFilterTask

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
        val isFiltering = requestAction == FilterConventions.actionFilterTask

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
            FilterConventions.actionParameter to FilterConventions.actionSummaryLookup)

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
    ): ProcessAction {
        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            FilterConventions.actionParameter to FilterConventions.actionLookupOutput)

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
                FilterConventions.actionSummaryTask

            ProcessTaskType.Filter ->
                FilterConventions.actionFilterTask
        }

        val result = ClientContext.clientRestTaskRepository.submit(
            state.mainLocation,
            DetachedRequest(
                RequestParams.of(
                    FilterConventions.actionParameter to action),
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
        val command = CriteriaSpec.addCommand(state.mainLocation, columnName)

        val result = ClientContext.mirroredGraphStore
            .apply(command)

        return when (result) {
            is MirroredGraphError ->
                FilterAddError(
                    result.error.message ?: "Failed: ${result.remote}")

            is MirroredGraphSuccess ->
                FilterAddResponse
        }
    }


    private suspend fun submitFilterRemove(
        state: ProcessState,
        columnName: String
    ): ProcessAction {
        val command = CriteriaSpec.removeCommand(state.mainLocation, columnName)

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
        val command = CriteriaSpec.addValueCommand(
            state.mainLocation, columnName, filterValue)

        val result = ClientContext.mirroredGraphStore.apply(command)

        val errorMessage =
            (result as? MirroredGraphError)?.error?.message

        return FilterUpdateResult(errorMessage)
    }


    private suspend fun submitFilterTypeChange(
        state: ProcessState,
        columnName: String,
        criteriaType: ColumnCriteriaType
    ): ProcessAction {
        val command = CriteriaSpec.updateTypeCommand(
            state.mainLocation, columnName, criteriaType)

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
        val command = CriteriaSpec.removeValueCommand(
            state.mainLocation, columnName, filterValue)

        val result = ClientContext.mirroredGraphStore.apply(command)

        val errorMessage =
            (result as? MirroredGraphError)?.error?.message

        return FilterUpdateResult(errorMessage)
    }
}