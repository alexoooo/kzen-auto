package tech.kzen.auto.server.objects.pipeline

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.objects.document.report.spec.input.InputDataSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationJvm.normalize
import tech.kzen.auto.server.objects.plugin.PluginUtils.asCommon
import tech.kzen.auto.server.objects.report.group.GroupPattern
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
        override fun next(arguments: TupleValue)/*: LogicResult*/ {
//            return LogicResultSuccess(TupleValue.ofMain("foo"))
        }

        override fun run(control: LogicControl): LogicResult {
            return LogicResultSuccess(TupleValue.ofMain("foo"))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val action = request.parameters.get(PipelineConventions.actionParameter)
            ?: return ExecutionFailure("'${PipelineConventions.actionParameter}' expected")

        return when (action) {
            PipelineConventions.actionBrowseFiles ->
                actionBrowserInfo()

            PipelineConventions.actionDefaultFormat ->
                actionDefaultFormat(request)

            PipelineConventions.actionInputInfo ->
                actionInputSelectionInfo()

            PipelineConventions.actionDataTypes ->
                actionDataTypes()

            PipelineConventions.actionTypeFormats ->
                actionTypeFormats()

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


    private fun actionDefaultFormat(request: ExecutionRequest): ExecutionResult {
        val filesParam = request.parameters.getAll(ReportConventions.filesParameter)
        val dataLocations = filesParam.map { DataLocation.of(it) }
        val dataType = input.selection.dataType

        val inputDataSpecs = mutableListOf<InputDataSpec>()
        for (dataLocation in dataLocations) {
            val defaultCoordinate = ServerContext.definitionRepository
                .find(dataType, dataLocation)
                .map { it.coordinate }
                .firstOrNull()
                ?: return ExecutionFailure("Unknown: $dataType - $dataLocation")

            val inputDataSpec = InputDataSpec(dataLocation, defaultCoordinate.asCommon())
            inputDataSpecs.add(inputDataSpec)
        }
        val asCollection = inputDataSpecs.map { it.asCollection() }

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(asCollection))
    }


    private fun actionInputSelectionInfo(): ExecutionResult {
        val groupPattern = GroupPattern.parse(input.selection.groupBy)
            ?: return ExecutionFailure("Group By pattern error: ${input.selection.groupBy}")

        val inputSelectionInfo = ServerContext.fileListingAction
            .selectionInfo(input.selection, groupPattern)
            .sorted()

        return ExecutionSuccess.ofValue(ExecutionValue.of(
            inputSelectionInfo.asCollection()))
    }


    private fun actionDataTypes(): ExecutionResult {
        val dataTypes = ServerContext.definitionRepository
            .listMetadata()
            .map { it.payloadType }
            .toSet()
            .map { it.toString() }

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(dataTypes))
    }


    private fun actionTypeFormats(): ExecutionResult {
        val dataType = input.selection.dataType

        val processorDefinerDetails = ServerContext.definitionRepository
            .listMetadata()
            .filter { it.payloadType == dataType }
            .map { it.toProcessorDefinerDetail() }

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(
                processorDefinerDetails.map { it.asCollection() }))
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