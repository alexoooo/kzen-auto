package tech.kzen.auto.client.objects.document.report.state

import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.common.objects.document.plugin.model.ProcessorDefinerDetail
import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.objects.document.report.listing.InputSelectionInfo
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisType
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueType
import tech.kzen.auto.common.objects.document.report.spec.filter.ColumnFilterType
import tech.kzen.auto.common.objects.document.report.spec.input.InputDataSpec
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.platform.ClassName

//---------------------------------------------------------------------------------------------------------------------
sealed class ReportAction {
    abstract fun flatten(): List<SingularReportAction>
}


sealed class SingularReportAction: ReportAction() {
    override fun flatten(): List<SingularReportAction> =
        listOf(this)

    override fun toString(): String {
        // NB: used by object (singleton) actions
        return this::class.simpleName!!
    }
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
//sealed class InitiateReportAction: SingularReportAction()


//object InitiateReportStart: InitiateReportAction()
object InitiateReport: SingularReportAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class InputReportAction: SingularReportAction()

//sealed class ListInputsResponse: ListInputsAction()


object EmptyInputSelection: InputReportAction()


//sealed class ListInputsAction: InputReportAction()


object ListInputsSelectedRequest: InputReportAction()


object ListInputsBrowserRequest: InputReportAction()


data class ListInputsBrowserNavigate(
    val newDirectory: DataLocation
): InputReportAction()


data class InputsBrowserFilterRequest(
    val filter: String
): InputReportAction()


data class InputsSelectionAddRequest(
    val paths: List<InputDataSpec>
): InputReportAction()


data class InputsSelectionRemoveRequest(
    val paths: List<InputDataSpec>
): InputReportAction()


data class InputsSelectionDataTypeRequest(
    val dataType: ClassName
): InputReportAction()


data class InputsSelectionGroupByRequest(
    val groupBy: String
): InputReportAction()


data class InputsSelectionFormatRequest(
    val format: CommonPluginCoordinate,
    val dataLocations: List<DataLocation>
): InputReportAction()


data class InputsSelectionMultiFormatRequest(
    val locationFormats: Map<DataLocation, CommonPluginCoordinate>
): InputReportAction()


data class ListInputsBrowserResult(
    val inputBrowserInfo: InputBrowserInfo
): InputReportAction()


data class ListInputsSelectedResult(
    val inputSelectionInfo: InputSelectionInfo
): InputReportAction()


data class ListInputsError(
    val message: String
): InputReportAction()


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
sealed class FormulaAction: SingularReportAction()


sealed class FormulaUpdateRequest: FormulaAction()


data class FormulaUpdateResult(
    override val errorMessage: String?
): FormulaAction(), ReportUpdateResult


//--------------------------------------------------------------
data class FormulaAddRequest(
    val columnName: String
): FormulaUpdateRequest()


data class FormulaRemoveRequest(
    val columnName: String
): FormulaUpdateRequest()


data class FormulaValueUpdateRequest(
    val columnName: String,
    val formula: String
): FormulaUpdateRequest()


//--------------------------------------------------------------
object FormulaValidationRequest: FormulaAction()


data class FormulaValidationResult(
    val messages: Map<String, String>,
    val errorMessage: String?
): FormulaAction()


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
sealed class AnalysisAction: SingularReportAction()


sealed class AnalysisUpdateRequest: AnalysisAction()


data class AnalysisUpdateResult(
    override val errorMessage: String?
): AnalysisAction(), ReportUpdateResult


//--------------------------------------------------------------
data class AnalysisChangeTypeRequest(
    val analysisType: AnalysisType
): AnalysisUpdateRequest()


//--------------------------------------------------------------
data class PivotRowAddRequest(
    val columnName: String
): AnalysisUpdateRequest()


data class PivotRowRemoveRequest(
    val columnName: String
): AnalysisUpdateRequest()


object PivotRowClearRequest: AnalysisUpdateRequest()


//--------------------------------------------------------------
data class PivotValueAddRequest(
    val columnName: String
): AnalysisUpdateRequest()


data class PivotValueRemoveRequest(
    val columnName: String
): AnalysisUpdateRequest()


data class PivotValueTypeAddRequest(
    val columnName: String,
    val valueType: PivotValueType
): AnalysisUpdateRequest()


data class PivotValueTypeRemoveRequest(
    val columnName: String,
    val valueType: PivotValueType
): AnalysisUpdateRequest()


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


object ReportProgressReset: ReportTaskAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class SummaryLookupAction: SingularReportAction()


object SummaryLookupRequest: SummaryLookupAction()


data class SummaryLookupResult(
    val tableSummary: TableSummary
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


//---------------------------------------------------------------------------------------------------------------------
sealed class ReportPluginAction: SingularReportAction()


data class PluginPathInfoRequest(
    val paths: List<DataLocation>
): ReportPluginAction()


data class PluginPathInfoResult(
    val paths: List<InputDataSpec>?,
    val errorMessage: String?
): ReportPluginAction()


object PluginDataTypesRequest: ReportPluginAction()


data class PluginDataTypesResult(
    val dataTypes: List<ClassName>?,
    val errorMessage: String?
): ReportPluginAction()


object PluginFormatsRequest: ReportPluginAction()


data class PluginFormatsResult(
    val formats: List<ProcessorDefinerDetail>?,
    val errorMessage: String?
): ReportPluginAction()

