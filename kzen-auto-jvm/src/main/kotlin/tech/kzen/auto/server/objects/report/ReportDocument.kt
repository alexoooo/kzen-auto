package tech.kzen.auto.server.objects.report

import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.AnalysisColumnInfo
import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.PreviewSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisType
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
import tech.kzen.auto.server.objects.plugin.PluginUtils.asCommon
import tech.kzen.auto.server.objects.plugin.PluginUtils.asPluginCoordinate
import tech.kzen.auto.server.objects.report.exec.input.connect.file.FileFlatDataSource
import tech.kzen.auto.server.objects.report.exec.input.model.data.DatasetInfo
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataHeaderDefinition
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataInfo
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataLocation
import tech.kzen.auto.server.objects.report.exec.output.TableReportOutput
import tech.kzen.auto.server.objects.report.exec.summary.ReportSummary
import tech.kzen.auto.server.objects.report.model.GroupPattern
import tech.kzen.auto.server.objects.report.model.ReportRunContext
import tech.kzen.auto.server.objects.report.service.ReportUtils
import tech.kzen.auto.server.objects.report.service.ReportWorkPool
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
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.io.path.Path


// TODO: consider charting support
//  https://github.com/JetBrains/lets-plot-kotlin
//  https://github.com/JetBrains/lets-plot-kotlin/issues/46
//  https://github.com/JetBrains/lets-plot-kotlin/issues/5
@Reflect
class ReportDocument(
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
        private const val mimeTypeCsv = "text/csv"

        private fun patternErrorOrNull(errors: List<String>): String? {
            return errors.firstOrNull()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val action = request.parameters.get(ReportConventions.actionParameter)
            ?: return ExecutionFailure("'${ReportConventions.actionParameter}' expected")

        return when (action) {
            ReportConventions.actionBrowseFiles ->
                actionBrowserInfo()

            ReportConventions.actionDefaultFormat ->
                actionDefaultFormat(request)

            ReportConventions.actionInputInfo ->
                actionInputSelectionInfo()

            ReportConventions.actionDataTypes ->
                actionDataTypes()

            ReportConventions.actionTypeFormats ->
                actionTypeFormats()

            ReportConventions.actionListColumns ->
                actionColumnListing()

            ReportConventions.actionOutputInfoOffline ->
                actionOutputInfoOffline()

            ReportConventions.actionOutputInfoOnline ->
                actionOutputInfoOnline(request)

            ReportConventions.actionValidateFormulas ->
                actionValidateFormulas()

            ReportConventions.actionSummaryOffline ->
                actionSummaryOffline()

            ReportConventions.actionSummaryOnline ->
                actionSummaryOnline(request)

            ReportConventions.actionReset ->
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
            tech.kzen.auto.server.objects.report.ReportDocument.Companion.mimeTypeCsv
        )
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
        val datasetInfo = datasetInfo()
            ?: return ExecutionFailure("Please provide a valid inputs")

        val analysisColumnInfo = analysisColumnInfo(datasetInfo)

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(analysisColumnInfo.asCollection()))
    }


    private fun actionOutputReset(): ExecutionResult {
        val reportRunContext = reportRunContext()
            ?: return ExecutionFailure("Missing run")

        return try {
            ReportWorkPool.deleteDir(reportRunContext.runDir)
            ExecutionSuccess.empty
        }
        catch (e: Exception) {
            ExecutionFailure.ofException(e)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun actionOutputInfoOffline(): ExecutionResult {
        val reportRunContext = reportRunContext()
            ?: return ExecutionFailure("Missing run")

        val outputInfo = tech.kzen.auto.server.objects.report.ReportExecution.Companion.outputInfoOffline(
            reportRunContext,
            ServerContext.reportWorkPool
        )

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
            ReportConventions.actionParameter to ReportConventions.actionOutputInfoOnline,
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
    private fun actionValidateFormulas(): ExecutionResult {
        val reportRunContext = reportRunContext()
            ?: return ExecutionFailure("Missing run")

        val pluginCoordinates = reportRunContext.datasetInfo.items.map { it.processorPluginCoordinate }.toSet()
        val classLoaderHandle = ServerContext.definitionRepository
            .classLoaderHandle(pluginCoordinates, ClassLoader.getSystemClassLoader())

        val dataType = input.selection.dataType
        val flatHeaderListing = reportRunContext.datasetInfo.headerSuperset()

        return classLoaderHandle.use {
            val errors: Map<String, String> = reportRunContext.formula
                .formulas
                .mapValues { formula ->
                    ServerContext.calculatedColumnEval.validate(
                        formula.key,
                        formula.value,
                        flatHeaderListing,
                        dataType,
                        it.classLoader)
                }
                .filterValues { error -> error != null }
                .mapValues { e -> e.value!! }

            ExecutionSuccess.ofValue(
                ExecutionValue.of(errors))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun actionSummaryOffline(): ExecutionResult {
        val reportRunContext = reportRunContext()
            ?: return ExecutionFailure("Missing run")

        val response = ReportSummary.tableSummaryOffline(reportRunContext)
            ?: return ExecutionFailure("Missing summary")

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(response.toCollection()))
    }


    private fun actionSummaryOnline(request: ExecutionRequest): ExecutionResult {
        val runId: LogicRunId = request
            .getSingle(CommonRestApi.paramRunId)
            ?.let { LogicRunId(it) }
            ?: return ExecutionResult.failure("Not found: ${CommonRestApi.paramRunId}")

        val executionId: LogicExecutionId = request
            .getSingle(CommonRestApi.paramExecutionId)
            ?.let { LogicExecutionId(it) }
            ?: return ExecutionResult.failure("Not found: ${CommonRestApi.paramExecutionId}")

        val runExecutionParams = RequestParams.of(
            ReportConventions.actionParameter to ReportConventions.actionSummaryOnline,
            CommonRestApi.paramRunId to runId.value,
            CommonRestApi.paramExecutionId to executionId.value
        )

        return ServerContext.serverLogicController.request(
            runId,
            executionId,
            ExecutionRequest(runExecutionParams, null))
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

        var success = false
        try {
            val reportExecution = ReportExecution(
                reportRunContext, ServerContext.reportWorkPool, logicTraceHandle, logicRunExecutionId)

            reportExecution.init(logicControl)

            success = true
            return reportExecution
        }
        finally {
            if (! success) {
                ServerContext.reportWorkPool.updateRunStatus(reportRunContext.runDir, OutputStatus.Failed)
            }
        }
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

        val analysisColumnInfo = analysisColumnInfo(datasetInfo)

        val withoutRunDir = ReportRunContext(
            Path("."),
            selfLocation.documentPath.name,
            dataType,
            datasetInfo,
            analysisColumnInfo,
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


    private fun analysisColumnInfo(datasetInfo: DatasetInfo): AnalysisColumnInfo {
        val inputColumnNames = datasetInfo.headerSuperset().values
        val calculatedColumnNames = formula.formulas.keys.toList()
        val inputAndCalculatedColumnNames = inputColumnNames + calculatedColumnNames

        if (analysis.type != AnalysisType.FlatData) {
            return AnalysisColumnInfo(
                inputAndCalculatedColumnNames.associateWith { true },
                null,
                null)
        }

        val allowErrors = mutableListOf<String>()
        val allowPatterns = mutableListOf<Pattern>()
        for (allowPattern in analysis.flat.allowPatterns.withIndex()) {
            try {
                allowPatterns.add(Pattern.compile(allowPattern.value))
            }
            catch (e: PatternSyntaxException) {
                allowErrors.add("${allowPattern.index + 1}: ${e.message}")
            }
        }

        val excludeErrors = mutableListOf<String>()
        val excludePatterns = mutableListOf<Pattern>()
        for (excludePattern in analysis.flat.excludePatterns.withIndex()) {
            try {
                excludePatterns.add(Pattern.compile(excludePattern.value))
            }
            catch (e: PatternSyntaxException) {
                excludeErrors.add("${excludePattern.index + 1}: ${e.message}")
            }
        }

        val inputColumns = inputAndCalculatedColumnNames.associateWith { columnName ->
            val allow = allowPatterns.isEmpty() ||
                    allowPatterns.any { it.matcher(columnName).matches() }

            val exclude = excludePatterns.isNotEmpty() &&
                    excludePatterns.any { it.matcher(columnName).matches() }

            allow && ! exclude
        }

        return AnalysisColumnInfo(
            inputColumns,
            patternErrorOrNull(allowErrors),
            patternErrorOrNull(excludeErrors)
        )
    }
}