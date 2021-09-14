package tech.kzen.auto.server.objects.pipeline

import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.PreviewSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputDataSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExploreSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunId
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationJvm.normalize
import tech.kzen.auto.server.objects.logic.LogicTraceHandle
import tech.kzen.auto.server.objects.pipeline.exec.input.connect.file.FileFlatDataSource
import tech.kzen.auto.server.objects.pipeline.exec.input.model.data.DatasetInfo
import tech.kzen.auto.server.objects.pipeline.exec.input.model.data.FlatDataHeaderDefinition
import tech.kzen.auto.server.objects.pipeline.exec.input.model.data.FlatDataInfo
import tech.kzen.auto.server.objects.pipeline.exec.input.model.data.FlatDataLocation
import tech.kzen.auto.server.objects.pipeline.exec.output.TableReportOutput
import tech.kzen.auto.server.objects.pipeline.model.GroupPattern
import tech.kzen.auto.server.objects.pipeline.model.ReportRunContext
import tech.kzen.auto.server.objects.plugin.PluginUtils.asCommon
import tech.kzen.auto.server.objects.plugin.PluginUtils.asPluginCoordinate
import tech.kzen.auto.server.objects.report.ReportUtils
import tech.kzen.auto.server.objects.report.ReportWorkPool
import tech.kzen.auto.server.paradigm.detached.DetachedDownloadAction
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.auto.server.service.v1.Logic
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.LogicExecution
import tech.kzen.auto.server.service.v1.LogicHandle
import tech.kzen.auto.server.service.v1.model.LogicDefinition
import tech.kzen.auto.server.service.v1.model.LogicType
import tech.kzen.auto.server.service.v1.model.TupleDefinition
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.DateTimeUtils
import java.awt.geom.IllegalPathStateException
import java.nio.file.Paths
import kotlin.io.path.Path


@Reflect
class PipelineDocument(
    private val input: InputSpec,
    private val formula: FormulaSpec,
    private val previewAll: PreviewSpec,
    private val filter: FilterSpec,
    private val previewFiltered: PreviewSpec,
    private val analysis: AnalysisSpec,
    private val output: OutputSpec,

    private val selfLocation: ObjectLocation
):
    DocumentArchetype(),
    DetachedAction,
    DetachedDownloadAction,
    Logic
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val mimeTypeCsv = "text/csv"
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

            PipelineConventions.actionListColumns ->
                actionColumnListing()

            PipelineConventions.actionOutputInfoOffline ->
                actionOutputInfoOffline()

            PipelineConventions.actionOutputInfoOnline ->
                actionOutputInfoOnline(request)

//            ReportConventions.actionValidateFormulas ->
//                actionValidateFormulas()
//
//            ReportConventions.actionSummaryLookup ->
//                actionColumnSummaryLookup()


            PipelineConventions.actionReset ->
                actionOutputReset()

            else ->
                return ExecutionFailure("Unknown action: $action")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun executeDownload(request: ExecutionRequest): ExecutionDownloadResult {
        val reportRunContext = reportRunContext()
            ?: throw IllegalStateException("Missing run")

        val filenamePrefix = FormatUtils.sanitizeFilename(selfLocation.documentPath.name.value)
        val filenameSuffix = DateTimeUtils.filenameTimestamp()
        val filename = filenamePrefix + "_" + filenameSuffix + ".csv"
        
        return ExecutionDownloadResult(
            TableReportOutput.downloadCsvOffline(reportRunContext),
            filename,
            mimeTypeCsv)
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


    private fun actionColumnListing(): ExecutionResult {
        val inputPaths = datasetInfo()
            ?: return ExecutionFailure("Please provide a valid inputs")

        val columnNames = inputPaths.headerSuperset().values
        return ExecutionSuccess.ofValue(
            ExecutionValue.of(columnNames))
    }


    private fun actionOutputReset(): ExecutionResult {
        val reportRunContext = reportRunContext()
            ?: return ExecutionFailure("Missing run")

        return ServerContext.reportRunAction.delete(reportRunContext.runDir)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun actionOutputInfoOffline(): ExecutionResult {
        val reportRunContext = reportRunContext()
            ?: return ExecutionFailure("Missing run")

        val outputInfo = PipelineExecution.outputInfoOffline(
            reportRunContext,
            ServerContext.reportWorkPool)

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(outputInfo.toCollection()))
    }


    private fun actionOutputInfoOnline(request: ExecutionRequest): ExecutionResult {
        val runId: LogicRunId = request
            .getSingle(CommonRestApi.paramRunId)
            ?.let { LogicRunId(it) }
            ?: return ExecutionResult.failure("Not found: ${CommonRestApi.paramRunId}")

        val executionId: LogicExecutionId = request
            .getSingle(CommonRestApi.paramExecutionId)
            ?.let { LogicExecutionId(it) }
            ?: return ExecutionResult.failure("Not found: ${CommonRestApi.paramExecutionId}")

        val runExecutionParams = RequestParams.of(
            CommonRestApi.paramRunId to runId.value,
            CommonRestApi.paramExecutionId to executionId.value,
            OutputExploreSpec.previewStartKey to output.explore.previewStartZeroBased().toString(),
            OutputExploreSpec.previewRowCountKey to output.explore.previewCount.toString(),
        )

        val pivotValueParams = analysis.pivot.values.asRequest()

        val combinedParams = runExecutionParams.addAll(pivotValueParams)

        return ServerContext.serverLogicController.request(
            runId,
            executionId,
            ExecutionRequest(combinedParams, null))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun define(): LogicDefinition {
        return LogicDefinition(
            TupleDefinition.empty,
            TupleDefinition.ofMain(LogicType.string))
    }


    override fun execute(
        handle: LogicHandle,
        logicTraceHandle: LogicTraceHandle,
        logicRunExecutionId: LogicRunExecutionId,
        logicControl: LogicControl
    ): LogicExecution {
        val reportRunContext = reportRunContext()
            ?: throw IllegalStateException("Unable to create context")

        ServerContext.reportWorkPool.prepareRunDir(reportRunContext.runDir, logicRunExecutionId)

        val pipelineExecution = PipelineExecution(
            reportRunContext, ServerContext.reportWorkPool, logicTraceHandle, logicRunExecutionId)

        pipelineExecution.init(logicControl)

        return pipelineExecution
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reportRunContext(): ReportRunContext? {
        val datasetInfo = datasetInfo()
            ?: return null

        val dataType = input.selection.dataType

        val reportDir =
            try {
                Paths.get(output.workPath)
            }
            catch (e: IllegalPathStateException) {
                ReportWorkPool.defaultReportDir
            }

        val withoutRunDir = ReportRunContext(
            Path("."),
            selfLocation.documentPath.name,
            dataType,
            datasetInfo,
            formula,
            previewAll,
            filter,
            previewFiltered,
            analysis,
            output)
        val reportRunSignature = withoutRunDir.toSignature()

        val runDir = ServerContext.reportWorkPool.resolveRunDir(reportRunSignature, reportDir)

        return withoutRunDir.copy(
            runDir = runDir.toAbsolutePath().normalize())
    }


    private fun datasetInfo(): DatasetInfo? {
        val groupPattern = GroupPattern.parse(input.selection.groupBy)
            ?: GroupPattern.empty

        val items = mutableListOf<FlatDataInfo>()
        for (inputDataSpec in input.selection.locations) {
            val dataLocation = inputDataSpec.location

            val pluginCoordinate = inputDataSpec.processorDefinitionCoordinate.asPluginCoordinate()
            val processorDefinitionMetadata = ServerContext.definitionRepository.metadata(pluginCoordinate)
                ?: return null

            val dataEncoding = ReportUtils.encodingWithMetadata(inputDataSpec, processorDefinitionMetadata)

            val flatDataLocation = FlatDataLocation(
                dataLocation, dataEncoding)

            val cachedHeaderListing = ServerContext.columnListingAction.cachedHeaderListing(
                dataLocation, pluginCoordinate)

            val headerListing = cachedHeaderListing
                ?: run {
                    val classLoaderHandle = ServerContext.definitionRepository
                        .classLoaderHandle(setOf(pluginCoordinate), ClassLoader.getSystemClassLoader())

                    classLoaderHandle.use {
                        val processorDefinition = ServerContext.definitionRepository.define(
                            pluginCoordinate, it)

                        ServerContext.columnListingAction.headerListing(
                            FlatDataHeaderDefinition(
                                flatDataLocation,
                                FileFlatDataSource(),
                                processorDefinition),
                            pluginCoordinate
                        )
                    }
                }

            val fileGroup = groupPattern.extract(flatDataLocation.dataLocation.fileName())

            items.add(FlatDataInfo(flatDataLocation, headerListing, pluginCoordinate, fileGroup))
        }
        return DatasetInfo(items.sorted())
    }
}