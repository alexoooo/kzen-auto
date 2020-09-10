package tech.kzen.auto.client.objects.document.process.state

import tech.kzen.auto.common.objects.document.filter.OutputInfo
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.reactive.TableSummary
import tech.kzen.auto.common.paradigm.reactive.TaskProgress
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskModel


//---------------------------------------------------------------------------------------------------------------------
sealed class ProcessAction {
    abstract fun flatten(): List<SingularProcessAction>
}


sealed class SingularProcessAction: ProcessAction() {
    override fun flatten(): List<SingularProcessAction> =
        listOf(this)
}


data class CompoundProcessAction(
    val actions: List<ProcessAction>
):
    ProcessAction()
{
    constructor(vararg processActions: ProcessAction): this(processActions.asList())


    override fun flatten(): List<SingularProcessAction> {
        val builder = mutableListOf<SingularProcessAction>()
        flattenInto(builder)
        return builder
    }


    private fun flattenInto(builder: MutableList<SingularProcessAction>) {
        for (action in actions) {
            when (action) {
                is CompoundProcessAction ->
                    action.flattenInto(builder)

                is SingularProcessAction ->
                    builder.add(action)
            }
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
object InitiateProcessEffect: SingularProcessAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class ListInputsAction: SingularProcessAction()


object ListInputsRequest: ListInputsAction()


sealed class ListInputsResponse: ListInputsAction()


data class ListInputsResult(
    val fileListing: List<String>
): ListInputsResponse()


data class ListInputsError(
    val message: String
): ListInputsResponse()


//---------------------------------------------------------------------------------------------------------------------
sealed class ListColumnsAction: SingularProcessAction()


object ListColumnsRequest: ListColumnsAction()


data class ListColumnsResponse(
    val columnListing: List<String>
): ListColumnsAction()


data class ListColumnsError(
    val message: String
): ListColumnsAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class FilterAction: SingularProcessAction()


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


//--------------------------------------------------------------
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
sealed class ProcessTaskAction: SingularProcessAction()


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


data class ProcessTaskStopRequest(
    val taskId: TaskId
): ProcessTaskAction()


data class ProcessTaskStopResponse(
    val taskModel: TaskModel
): ProcessTaskAction()



//---------------------------------------------------------------------------------------------------------------------
sealed class SummaryLookupAction: SingularProcessAction()


object SummaryLookupRequest: SummaryLookupAction()


data class SummaryLookupResult(
    val tableSummary: TableSummary,
    val taskProgress: TaskProgress
): SummaryLookupAction()


data class SummaryLookupError(
    val errorMessage: String
): SummaryLookupAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class OutputLookupAction: SingularProcessAction()


object OutputLookupRequest: OutputLookupAction()


data class OutputLookupResult(
    val outputInfo: OutputInfo
): OutputLookupAction()


data class OutputLookupError(
    val errorMessage: String
): OutputLookupAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class ProcessRefreshAction: SingularProcessAction()


data class ProcessRefreshSchedule(
    val refreshAction: ProcessAction
): ProcessRefreshAction()


object ProcessRefreshCancel: ProcessRefreshAction()
