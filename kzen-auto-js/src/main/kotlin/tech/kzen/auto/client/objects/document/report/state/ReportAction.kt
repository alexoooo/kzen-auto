package tech.kzen.auto.client.objects.document.report.state

import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.spec.ColumnFilterType
import tech.kzen.auto.common.objects.document.report.spec.PivotValueType
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskModel


//---------------------------------------------------------------------------------------------------------------------
sealed class ReportAction {
    abstract fun flatten(): List<SingularReportAction>
}


sealed class SingularReportAction: ReportAction() {
    override fun flatten(): List<SingularReportAction> =
        listOf(this)
}


interface ReportUpdateResult {
    val errorMessage: String?
}


data class CompoundReportAction(
    val actions: List<ReportAction>
):
    ReportAction()
{
    companion object {
        fun of(vararg reportActions: ReportAction?): ReportAction? {
            return of(reportActions.asList())
        }

        @Suppress("MemberVisibilityCanBePrivate")
        fun of(actions: List<ReportAction?>): ReportAction? {
            val notNull = actions.filterNotNull()
            return when (notNull.size) {
                0 -> null
                1 -> notNull.single()
                else -> CompoundReportAction(notNull)
            }
        }
    }

    constructor(vararg reportActions: ReportAction): this(reportActions.asList())


    override fun flatten(): List<SingularReportAction> {
        val builder = mutableListOf<SingularReportAction>()
        flattenInto(builder)
        return builder
    }


    private fun flattenInto(builder: MutableList<SingularReportAction>) {
        for (action in actions) {
            when (action) {
                is CompoundReportAction ->
                    action.flattenInto(builder)

                is SingularReportAction ->
                    builder.add(action)
            }
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
sealed class InitiateReportAction: SingularReportAction()


object InitiateReportStart: InitiateReportAction()


object InitiateReportDone: InitiateReportAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class InputReportAction: SingularReportAction()


object InputsUpdatedRequest: InputReportAction()


sealed class ListInputsAction: InputReportAction()


object ListInputsRequest: ListInputsAction()


sealed class ListInputsResponse: ListInputsAction()


data class ListInputsResult(
    val fileListing: List<String>
): ListInputsResponse()


data class ListInputsError(
    val message: String
): ListInputsResponse()


//---------------------------------------------------------------------------------------------------------------------
sealed class ListColumnsAction: SingularReportAction()


object ListColumnsRequest: ListColumnsAction()


data class ListColumnsResponse(
    val columnListing: List<String>
): ListColumnsAction()


data class ListColumnsError(
    val message: String
): ListColumnsAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class FilterAction: SingularReportAction()


sealed class FilterUpdateRequest: FilterAction()


data class FilterUpdateResult(
    override val errorMessage: String?
): FilterAction(), ReportUpdateResult


//--------------------------------------------------------------
data class FilterAddRequest(
    val columnName: String
): FilterUpdateRequest()


data class FilterRemoveRequest(
    val columnName: String
): FilterUpdateRequest()


data class FilterValueAddRequest(
    val columnName: String,
    val filterValue: String
): FilterUpdateRequest()


data class FilterValueRemoveRequest(
    val columnName: String,
    val filterValue: String
): FilterUpdateRequest()


data class FilterTypeChangeRequest(
    val columnName: String,
    val filterType: ColumnFilterType
): FilterUpdateRequest()


//---------------------------------------------------------------------------------------------------------------------
sealed class PivotAction: SingularReportAction()


sealed class PivotUpdateRequest: PivotAction()


data class PivotUpdateResult(
    override val errorMessage: String?
): PivotAction(), ReportUpdateResult


//--------------------------------------------------------------
data class PivotRowAddRequest(
    val columnName: String
): PivotUpdateRequest()


data class PivotRowRemoveRequest(
    val columnName: String
): PivotUpdateRequest()


object PivotRowClearRequest: PivotUpdateRequest()


//--------------------------------------------------------------
data class PivotValueAddRequest(
    val columnName: String
): PivotUpdateRequest()


data class PivotValueRemoveRequest(
    val columnName: String
): PivotUpdateRequest()


data class PivotValueTypeAddRequest(
    val columnName: String,
    val valueType: PivotValueType
): PivotUpdateRequest()


data class PivotValueTypeRemoveRequest(
    val columnName: String,
    val valueType: PivotValueType
): PivotUpdateRequest()


//---------------------------------------------------------------------------------------------------------------------
sealed class ReportTaskAction: SingularReportAction()


object ReportTaskLookupRequest: ReportTaskAction()


data class ReportTaskLookupResponse(
    val taskModel: TaskModel?
): ReportTaskAction()


data class ReportTaskRunRequest(
    val type: ReportTaskType
): ReportTaskAction()


data class ReportTaskRunResponse(
    val taskModel: TaskModel
): ReportTaskAction()


data class ReportTaskRefreshRequest(
    val taskId: TaskId
): ReportTaskAction()


data class ReportTaskRefreshResponse(
    val taskModel: TaskModel?
): ReportTaskAction()


data class ReportTaskStopRequest(
    val taskId: TaskId
): ReportTaskAction()


data class ReportTaskStopResponse(
    val taskModel: TaskModel
): ReportTaskAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class SummaryLookupAction: SingularReportAction()


object SummaryLookupRequest: SummaryLookupAction()


data class SummaryLookupResult(
    val tableSummary: TableSummary/*,
    val taskProgress: TaskProgress*/
): SummaryLookupAction()


data class SummaryLookupError(
    val errorMessage: String
): SummaryLookupAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class OutputLookupAction: SingularReportAction()


object OutputLookupRequest: OutputLookupAction()


data class OutputLookupResult(
    val outputInfo: OutputInfo
): OutputLookupAction()


data class OutputLookupError(
    val errorMessage: String
): OutputLookupAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class ReportRefreshAction: SingularReportAction()


data class ReportRefreshSchedule(
    val refreshAction: ReportAction
): ReportRefreshAction()


object ReportRefreshCancel: ReportRefreshAction()



//---------------------------------------------------------------------------------------------------------------------
object ReportSaveAction: SingularReportAction()


//---------------------------------------------------------------------------------------------------------------------
object ReportResetAction: SingularReportAction()


data class ReportResetResult(
    override val errorMessage: String?
): SingularReportAction(), ReportUpdateResult