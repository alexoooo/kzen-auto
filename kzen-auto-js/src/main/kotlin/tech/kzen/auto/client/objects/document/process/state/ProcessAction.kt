package tech.kzen.auto.client.objects.document.process.state

import tech.kzen.auto.common.objects.document.filter.OutputInfo
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.reactive.TableSummary
import tech.kzen.auto.common.paradigm.reactive.TaskProgress
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskModel


//---------------------------------------------------------------------------------------------------------------------
sealed class ProcessAction


//---------------------------------------------------------------------------------------------------------------------
object InitiateProcessEffect: ProcessAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class ListInputsAction: ProcessAction()


object ListInputsRequest: ListInputsAction()


sealed class ListInputsResponse: ListInputsAction()


data class ListInputsResult(
    val fileListing: List<String>
): ListInputsResponse()


data class ListInputsError(
    val message: String
): ListInputsResponse()


//---------------------------------------------------------------------------------------------------------------------
sealed class ListColumnsAction: ProcessAction()


object ListColumnsRequest: ListColumnsAction()


data class ListColumnsResponse(
    val columnListing: List<String>
): ListColumnsAction()


data class ListColumnsError(
    val message: String
): ListColumnsAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class FilterAction: ProcessAction()


//--------------------------------------------------------------
data class FilterAddRequest(
    val columnName: String
): FilterAction()


object FilterAddResponse: FilterAction()


data class FilterAddError(
    val message: String
): FilterAction()


//--------------------------------------------------------------
data class FilterRemoveRequest(
    val columnName: String
): FilterAction()


//object FilterRemoveResponse: FilterAction()
//
//
//data class FilterRemoveError(
//    val message: String
//): FilterAction()


//--------------------------------------------------------------
//data class FilterUpdateRequest(
//    val columnName: String,
//    val filterValues: List<String>
//): FilterAction()


data class FilterValueAddRequest(
    val columnName: String,
    val filterValue: String
): FilterAction()


data class FilterValueRemoveRequest(
    val columnName: String,
    val filterValue: String
): FilterAction()


data class FilterUpdateResult(
    val errorMessage: String?
): FilterAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class ProcessTaskAction: ProcessAction()


object ProcessTaskLookupRequest: ProcessTaskAction()


data class ProcessTaskLookupResponse(
    val taskModel: TaskModel?
): ProcessTaskAction()


data class ProcessTaskRunRequest(
    val type: ProcessTaskType
): ProcessTaskAction()


data class ProcessTaskRunResponse(
    val taskModel: TaskModel
): ProcessTaskAction()


data class ProcessTaskRefreshRequest(
    val taskId: TaskId
): ProcessTaskAction()


data class ProcessTaskRefreshResponse(
    val taskModel: TaskModel?
): ProcessTaskAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class SummaryLookupAction: ProcessAction()


object SummaryLookupRequest: SummaryLookupAction()


data class SummaryLookupResult(
    val tableSummary: TableSummary,
    val taskProgress: TaskProgress
): SummaryLookupAction()


data class SummaryLookupError(
    val errorMessage: String
): SummaryLookupAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class OutputLookupAction: ProcessAction()


object OutputLookupRequest: OutputLookupAction()


data class OutputLookupResult(
    val outputInfo: OutputInfo
): OutputLookupAction()


data class OutputLookupError(
    val errorMessage: String
): OutputLookupAction()


//---------------------------------------------------------------------------------------------------------------------
data class ProcessRefreshSchedule(
    val refreshAction: ProcessAction
): ProcessAction()
