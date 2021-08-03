package tech.kzen.auto.server.objects.pipeline

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.util.data.DataLocationJvm.normalize
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.auto.server.service.v1.Logic
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.LogicExecution
import tech.kzen.auto.server.service.v1.LogicHandle
import tech.kzen.auto.server.service.v1.model.*
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class PipelineDocument(
    private val input: InputSpec,

    private val selfLocation: ObjectLocation
):
    DocumentArchetype(),
    DetachedAction,
    Logic
{
    //-----------------------------------------------------------------------------------------------------------------
    private class Execution: LogicExecution {
        override fun next(arguments: TupleValue): LogicResult {
            return LogicResultSuccess(TupleValue.ofMain("foo"))
        }

        override fun run(control: LogicControl): LogicResult {
            throw IllegalStateException()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val action = request.parameters.get(PipelineConventions.actionParameter)
            ?: return ExecutionFailure("'${PipelineConventions.actionParameter}' expected")

        return when (action) {
            PipelineConventions.actionBrowseFiles ->
                actionBrowserInfo()

//            ReportConventions.actionDataTypes ->
//                actionDataTypes()
//
//            ReportConventions.actionTypeFormats ->
//                actionTypeFormats()
//
//            ReportConventions.actionDefaultFormat ->
//                actionDefaultFormat(request)
//
//            ReportConventions.actionInputInfo ->
//                actionInputInfo()
//
//            ReportConventions.actionListColumns ->
//                actionColumnListing()
//
//            ReportConventions.actionValidateFormulas ->
//                actionValidateFormulas()
//
//            ReportConventions.actionSummaryLookup ->
//                actionColumnSummaryLookup()
//
//            ReportConventions.actionLookupOutput ->
//                actionOutputInfo()
//
//            ReportConventions.actionSave ->
//                actionSave()
//
//            ReportConventions.actionReset ->
//                actionReset()

            else ->
                return ExecutionFailure("Unknown action: $action")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun actionBrowserInfo(): ExecutionResult {
        val absoluteDir = input.browser.directory.normalize()
        val inputPaths = ServerContext.fileListingAction.scanInfo(
            input.browser.directory, input.browser.filter)

        val inputInfo = InputBrowserInfo(
            absoluteDir, inputPaths)

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(inputInfo.asCollection()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun define(): LogicDefinition {
        return LogicDefinition(
            TupleDefinition.empty,
            TupleDefinition.ofMain(LogicType.string))
    }


    override fun execute(handle: LogicHandle): LogicExecution {
        return Execution()
    }
}