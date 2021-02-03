package tech.kzen.auto.client.objects.document.report.state

import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.InputInfo
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.spec.*
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskState
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphSuccess


object ReportEffect {
    //-----------------------------------------------------------------------------------------------------------------
    val refreshView = CompoundReportAction(
        SummaryLookupRequest, OutputLookupRequest)


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun effect(
        state: ReportState,
//        prevState: ProcessState,
        action: SingularReportAction
    ): ReportAction? {
//        console.log("ReportEffect action: ", action)

        if (action == InitiateReport ||
                action == InputsUpdatedRequest
        ) {
            return CompoundReportAction(
                ListInputsSelectedRequest,
                OutputLookupRequest)
        }

        return when (action) {
            is OutputLookupRequest ->
                lookupOutput(state)


            ListInputsSelectedRequest ->
                loadInputInfo(state)

            ListInputsBrowserRequest ->
                loadBrowserFiles(state)

            is ListInputsBrowserNavigate ->
                navigateBrowserFiles(state, action.newDirectory)

            is InputsSelectionAddRequest ->
                selectBrowserFiles(state, action.paths)

            is InputsSelectionRemoveRequest ->
                unselectBrowserFiles(state, action.paths)

            is InputsSelectionFilterRequest ->
                updateBrowserFilter(state, action.filter)

//            is ListInputsResponse ->
//                ListColumnsRequest


            ListColumnsRequest ->
                loadColumnListing(state)

            is ListColumnsResponse ->
                if (action.columnListing.isNotEmpty()) {
                    ReportTaskLookupRequest
                }
                else {
                    null
                }


            ReportTaskLookupRequest ->
                loadTask(state)

            is ReportTaskLookupResponse ->
                taskLoaded(action)


            SummaryLookupRequest ->
                lookupSummary(state)


            is ReportTaskRunRequest ->
                runTask(state, action.type)

            is ReportTaskRunResponse ->
                taskRunning(action)

            is ReportTaskRefreshRequest ->
                refreshTask(action.taskId)

            is ReportTaskRefreshResponse ->
                refreshTaskLoop(action)

            is ReportTaskStopRequest ->
                stopTask(action)

            is ReportTaskStopResponse ->
                taskStopped(/*action*/)


            is FormulaAddRequest ->
                submitFormulaAdd(state, action.columnName)

            is FormulaRemoveRequest ->
                submitFormulaRemove(state, action.columnName)

//            is FormulaValueUpdateRequest ->
//                submitFormulaValueUpdate(state, action.columnName, action.formula)

            is FormulaValidationRequest ->
                validateFormulasAction(state)


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

            is ReportUpdateResult -> {
//                console.log("%%%% ReportUpdateResult - $action")
                if (action.errorMessage == null) {
                    refreshView
                }
                else {
                    null
                }
            }

            ReportSaveAction ->
                reportSaveAction(state)

            ReportResetAction ->
                reportResetAction(state)

            else -> null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun loadInputInfo(
        state: ReportState
    ): ReportAction {
        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            ReportConventions.actionParameter to ReportConventions.actionInputInfo)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, Any>

                ListInputsSelectedResult(
                    InputInfo.fromCollection(resultValue))
            }

            is ExecutionFailure -> {
                ListInputsError(result.errorMessage)
            }
        }
    }


    private suspend fun loadBrowserFiles(
        state: ReportState
    ): ReportAction {
        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            ReportConventions.actionParameter to ReportConventions.actionBrowseFiles)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, Any>

                ListInputsBrowserResult(
                    InputInfo.fromCollection(resultValue))
            }

            is ExecutionFailure -> {
                ListInputsError(result.errorMessage)
            }
        }
    }


    private suspend fun navigateBrowserFiles(
        state: ReportState,
        newDirectory: String
    ): ReportAction {
        val command = InputSpec.browseCommand(state.mainLocation, newDirectory)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return when (result) {
            is MirroredGraphSuccess -> {
                ListInputsBrowserRequest
            }

            is MirroredGraphError ->
                ListInputsError(result.error.message ?: "error")
        }
    }


    private suspend fun selectBrowserFiles(
        state: ReportState,
        paths: List<String>
    ): ReportAction {
        val command = InputSpec.addSelectedCommand(state.mainLocation, paths)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return when (result) {
            is MirroredGraphSuccess -> {
                ListInputsSelectedRequest
            }

            is MirroredGraphError ->
                ListInputsError(result.error.message ?: "error")
        }
    }


    private suspend fun unselectBrowserFiles(
        state: ReportState,
        paths: List<String>
    ): ReportAction {
        val command = InputSpec.removeSelectedCommand(state.mainLocation, paths)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return when (result) {
            is MirroredGraphSuccess -> {
                ListInputsSelectedRequest
            }

            is MirroredGraphError ->
                ListInputsError(result.error.message ?: "error")
        }
    }


    private suspend fun updateBrowserFilter(
        state: ReportState,
        filter: String
    ): ReportAction {
        val command = InputSpec.filterCommand(state.mainLocation, filter)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return when (result) {
            is MirroredGraphSuccess -> {
                ListInputsBrowserRequest
            }

            is MirroredGraphError ->
                ListInputsError(result.error.message ?: "error")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun loadColumnListing(
        state: ReportState
    ): ReportAction? {
        if (state.inputSelected == null ||
                state.inputSelected.isEmpty()) {
            return null
        }

        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            ReportConventions.actionParameter to ReportConventions.actionListColumns)

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
        state: ReportState
    ): ReportTaskLookupResponse {
        val activeTasks = ClientContext.clientRestTaskRepository.lookupActive(state.mainLocation)

        if (activeTasks.isEmpty()) {
            return ReportTaskLookupResponse(null)
        }

        val taskId = activeTasks.single()
        val model = ClientContext.clientRestTaskRepository.query(taskId)!!

        return ReportTaskLookupResponse(model)
    }


    private fun taskLoaded(
        action: ReportTaskLookupResponse
    ): ReportAction? {
        val taskModel = action.taskModel
            ?: return CompoundReportAction.of(
                SummaryLookupRequest, FormulaValidationRequest)

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
                ReportRefreshSchedule(
                    ReportTaskRefreshRequest(action.taskModel.taskId))
            }
            else {
                null
            }

        return CompoundReportAction.of(firstAction, FormulaValidationRequest, secondAction)
    }


    private suspend fun refreshTask(
        taskId: TaskId
    ): ReportAction {
        val model = ClientContext.clientRestTaskRepository.query(taskId)
        return ReportTaskRefreshResponse(model)
    }


    private fun refreshTaskLoop(
        action: ReportTaskRefreshResponse
    ): ReportAction? {
        val taskModel = action.taskModel
            ?: return null

        if (taskModel.state == TaskState.Running) {
            return ReportRefreshSchedule(
                ReportTaskRefreshRequest(action.taskModel.taskId))
        }

//        console.log("$%%$#%$# task done")
        return CompoundReportAction(
            SummaryLookupRequest, OutputLookupRequest)
    }


    private suspend fun stopTask(
        action: ReportTaskStopRequest
    ): ReportTaskStopResponse? {
        val taskModel = ClientContext.clientRestTaskRepository.cancel(action.taskId)
            ?: return null

        return ReportTaskStopResponse(taskModel)
    }


    private fun taskStopped(
//        action: ReportTaskStopResponse
    ): ReportAction {
//        val requestAction = action.taskModel.requestAction()
//        val isFiltering = requestAction == ReportConventions.actionRunTask

        return CompoundReportAction(
            ReportRefreshCancel, OutputLookupRequest)
//        return if (isFiltering) {
//            CompoundReportAction(
//                ReportRefreshCancel/*, OutputLookupRequest*/)
//        }
//        else {
//            ReportRefreshCancel
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun lookupSummary(
        state: ReportState
    ): SummaryLookupAction {
//        console.log("^$^$^$% requesting SummaryLookup")

        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            ReportConventions.actionParameter to ReportConventions.actionSummaryLookup)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, Map<String, Any>>
                val tableSummary = TableSummary.fromCollection(resultValue)

//                @Suppress("UNCHECKED_CAST")
//                val resultDetail = result.detail.get() as Map<String, String>
//                val summaryProgress = TaskProgress.fromCollection(resultDetail)

                SummaryLookupResult(
                    tableSummary/*, summaryProgress*/)
            }

            is ExecutionFailure -> {
                SummaryLookupError(result.errorMessage)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun lookupOutput(
        state: ReportState
    ): ReportAction? {
        if (state.columnListing.isNullOrEmpty()) {
            return null
        }

        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            ReportConventions.actionParameter to ReportConventions.actionLookupOutput)

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
        state: ReportState,
        type: ReportTaskType
    ): ReportAction {
        val action = when (type) {
//            ProcessTaskType.Index ->
//                ProcessConventions.actionSummaryTask

            ReportTaskType.RunReport ->
                ReportConventions.actionRunTask
        }

        val result = ClientContext.clientRestTaskRepository.submit(
            state.mainLocation,
            DetachedRequest(
                RequestParams.of(
                    ReportConventions.actionParameter to action),
                null))

        return ReportTaskRunResponse(result)
    }


    private fun taskRunning(
        action: ReportTaskRunResponse
    ): ReportAction? {
        if (action.taskModel.state != TaskState.Running) {
            return null
        }

        return CompoundReportAction(
            ReportRefreshSchedule(
                ReportTaskRefreshRequest(action.taskModel.taskId)),
            OutputLookupRequest)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun submitFormulaAdd(
        state: ReportState,
        columnName: String
    ): ReportAction {
        val command = FormulaSpec.addCommand(state.mainLocation, columnName)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return when (result) {
            is MirroredGraphError ->
                FormulaUpdateResult(
                    result.error.message ?: "Failed: ${result.remote}")

            is MirroredGraphSuccess ->
                FormulaUpdateResult(null)
        }
    }


    private suspend fun submitFormulaRemove(
        state: ReportState,
        columnName: String
    ): ReportAction {
        val command = FormulaSpec.removeCommand(state.mainLocation, columnName)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return when (result) {
            is MirroredGraphError ->
                FormulaUpdateResult(
                    result.error.message ?: "Failed: ${result.remote}")

            is MirroredGraphSuccess ->
                FormulaUpdateResult(null)
        }
    }


//    private suspend fun submitFormulaValueUpdate(
//        state: ReportState,
//        columnName: String,
//        formula: String
//    ): ReportAction {
//        val command = FormulaSpec.updateFormulaCommand(
//            state.mainLocation, columnName, formula)
//
//        val result = ClientContext.mirroredGraphStore.apply(command)
//
//        val errorMessage =
//            (result as? MirroredGraphError)?.error?.message
//
//        return FilterUpdateResult(errorMessage)
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun submitFilterAdd(
        state: ReportState,
        columnName: String
    ): ReportAction {
        val command = FilterSpec.addCommand(state.mainLocation, columnName)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return when (result) {
            is MirroredGraphError ->
                FilterUpdateResult(
                    result.error.message ?: "Failed: ${result.remote}")

            is MirroredGraphSuccess ->
                FilterUpdateResult(null)
        }
    }


    private suspend fun submitFilterRemove(
        state: ReportState,
        columnName: String
    ): ReportAction {
        val command = FilterSpec.removeCommand(state.mainLocation, columnName)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return when (result) {
            is MirroredGraphError ->
                FilterUpdateResult(
                    result.error.message ?: "Failed: ${result.remote}")

            is MirroredGraphSuccess ->
                FilterUpdateResult(null)
        }
    }


    private suspend fun submitFilterValueAdd(
        state: ReportState,
        columnName: String,
        filterValue: String
    ): ReportAction {
        val command = FilterSpec.addValueCommand(
            state.mainLocation, columnName, filterValue)

        val result = ClientContext.mirroredGraphStore.apply(command)

        val errorMessage =
            (result as? MirroredGraphError)?.error?.message

        return FilterUpdateResult(errorMessage)
    }


    private suspend fun submitFilterTypeChange(
        state: ReportState,
        columnName: String,
        filterType: ColumnFilterType
    ): ReportAction {
        val command = FilterSpec.updateTypeCommand(
            state.mainLocation, columnName, filterType)

        val result = ClientContext.mirroredGraphStore.apply(command)

        val errorMessage =
            (result as? MirroredGraphError)?.error?.message

        return FilterUpdateResult(errorMessage)
    }


    private suspend fun submitFilterValueRemove(
        state: ReportState,
        columnName: String,
        filterValue: String
    ): ReportAction {
        val command = FilterSpec.removeValueCommand(
            state.mainLocation, columnName, filterValue)

        val result = ClientContext.mirroredGraphStore.apply(command)

        val errorMessage =
            (result as? MirroredGraphError)?.error?.message

        return FilterUpdateResult(errorMessage)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun submitPivotRowAdd(state: ReportState, columnName: String): ReportAction {
        return submitPivotUpdate(
            PivotSpec.addRowCommand(
            state.mainLocation, columnName))
    }


    private suspend fun submitPivotRowRemove(state: ReportState, columnName: String): ReportAction {
        return submitPivotUpdate(
            PivotSpec.removeRowCommand(
            state.mainLocation, columnName))
    }


    private suspend fun submitPivotRowClear(state: ReportState): ReportAction {
        return submitPivotUpdate(
            PivotSpec.clearRowCommand(
            state.mainLocation))
    }


    private suspend fun submitPivotValueAdd(state: ReportState, columnName: String): ReportAction {
        return submitPivotUpdate(
            PivotSpec.addValueCommand(
            state.mainLocation, columnName))
    }


    private suspend fun submitPivotValueRemove(state: ReportState, columnName: String): ReportAction {
        return submitPivotUpdate(
            PivotSpec.removeValueCommand(
            state.mainLocation, columnName))
    }


    private suspend fun submitPivotValueTypeAdd(
        state: ReportState, columnName: String, valueType: PivotValueType
    ): ReportAction {
        return submitPivotUpdate(
            PivotSpec.addValueTypeCommand(
            state.mainLocation, columnName, valueType))
    }


    private suspend fun submitPivotValueTypeRemove(
        state: ReportState, columnName: String, valueType: PivotValueType
    ): ReportAction {
        return submitPivotUpdate(
            PivotSpec.removeValueTypeCommand(
            state.mainLocation, columnName, valueType))
    }


    private suspend fun submitPivotUpdate(
        pivotUpdateCommand: NotationCommand
    ): ReportAction {
        val result = ClientContext.mirroredGraphStore.apply(pivotUpdateCommand)

        val errorMessage =
            (result as? MirroredGraphError)?.error?.message

        return PivotUpdateResult(errorMessage)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun reportSaveAction(
        state: ReportState
    ): ReportAction? {
//        if (state.fileListing.isNullOrEmpty()) {
//            return null
//        }

//        val result =
        ClientContext.restClient.performDetached(
            state.mainLocation,
            ReportConventions.actionParameter to ReportConventions.actionSave)

        return null
//        return when (result) {
//            is ExecutionSuccess -> {
//                @Suppress("UNCHECKED_CAST")
//                val resultValue = result.value.get() as List<String>
//
//                ListColumnsResponse(resultValue)
//            }
//
//            is ExecutionFailure -> {
//                ListColumnsError(result.errorMessage)
//            }
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun reportResetAction(
        state: ReportState
    ): ReportAction {
//        if (state.fileListing.isNullOrEmpty()) {
//            return null
//        }

        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            ReportConventions.actionParameter to ReportConventions.actionReset)

        return when (result) {
            is ExecutionSuccess -> {
//                @Suppress("UNCHECKED_CAST")
//                val resultValue = result.value.get() as List<String>
                ReportResetResult(null)
            }

            is ExecutionFailure -> {
                ReportResetResult(result.errorMessage)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun validateFormulasAction(
        state: ReportState
    ): ReportAction {
//        if (state.fileListing.isNullOrEmpty()) {
//            return null
//        }

        val result = ClientContext.restClient.performDetached(
            state.mainLocation,
            ReportConventions.actionParameter to ReportConventions.actionValidateFormulas)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, String>
                FormulaValidationResult(resultValue, null)
            }

            is ExecutionFailure -> {
                FormulaValidationResult(
                    mapOf(),
                    result.errorMessage)
            }
        }
    }
}