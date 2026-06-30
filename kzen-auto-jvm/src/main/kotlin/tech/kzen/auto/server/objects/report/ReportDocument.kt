package tech.kzen.auto.server.objects.report

import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.*
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
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationJvm.normalize
import tech.kzen.auto.server.exec.report.ReportRun
import tech.kzen.auto.server.objects.plugin.PluginUtils.asCommon
import tech.kzen.auto.server.objects.plugin.PluginUtils.asPluginCoordinate
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumnEval
import tech.kzen.auto.server.objects.report.exec.input.connect.file.FileFlatDataSource
import tech.kzen.auto.server.objects.report.exec.input.model.data.DatasetInfo
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataHeaderDefinition
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataInfo
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataLocation
import tech.kzen.auto.server.objects.report.exec.output.TableReportOutput
import tech.kzen.auto.server.objects.report.exec.summary.ReportSummary
import tech.kzen.auto.server.objects.report.model.GroupPattern
import tech.kzen.auto.server.objects.report.model.ReportRunContext
import tech.kzen.auto.server.objects.report.service.ColumnListingAction
import tech.kzen.auto.server.objects.report.service.FileListingAction
import tech.kzen.auto.server.objects.report.service.ReportUtils
import tech.kzen.auto.server.objects.report.service.ReportWorkPool
import tech.kzen.auto.server.paradigm.detached.DetachedDownloadAction
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.auto.server.service.impl.ServerLogicController
import tech.kzen.auto.server.service.plugin.ReportDefinitionRepository
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.*
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
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

    private val selfLocation: ObjectLocation,

    @Service private val reportWorkPool: ReportWorkPool,
    @Service private val definitionRepository: ReportDefinitionRepository,
    @Service private val calculatedColumnEval: CalculatedColumnEval,
    @Service private val fileListingAction: FileListingAction,
    @Service private val columnListingAction: ColumnListingAction,
    @Service private val serverLogicController: ServerLogicController
):
    DocumentArchetype(),
    DetachedAction,
    DetachedDownloadAction
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        @Suppress("ConstPropertyName")
        private const val mimeTypeCsv = "text/csv"

        private fun patternErrorOrNull(errors: List<String>): String? {
            return errors.firstOrNull()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val action = request.parameters.get(ReportConventions.paramAction)
            ?: return ExecutionFailure("'${ReportConventions.paramAction}' expected")

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
                ExecutionFailure("Unknown action: $action")
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
            mimeTypeCsv
        )
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun actionBrowserInfo(): ExecutionResult {
        val absoluteDir = input.browser.directory.normalize()
        val inputPaths = fileListingAction.scanInfo(
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
            val defaultCoordinate = definitionRepository
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

        val inputSelectionInfo = fileListingAction
            .selectionInfo(input.selection, groupPattern)
            .sorted()

        return ExecutionSuccess.ofValue(ExecutionValue.of(
            inputSelectionInfo.asCollection()))
    }


    private fun actionDataTypes(): ExecutionResult {
        val dataTypes = definitionRepository
            .listMetadata()
            .map { it.payloadType }
            .toSet()
            .map { it.toString() }

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(dataTypes))
    }


    private fun actionTypeFormats(): ExecutionResult {
        val dataType = input.selection.dataType

        val processorDefinerDetails = definitionRepository
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

        val outputInfo = ReportRun.outputInfoOffline(
            reportRunContext,
            reportWorkPool
        )

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(outputInfo.toCollection()))
    }


    private fun actionOutputInfoOnline(request: ExecutionRequest): ExecutionResult {
        val runId: LogicRunId = request
            .getSingle(CommonRestApi.paramRunId)
            ?.let { LogicRunId(it) }
            ?: return ExecutionResult.failure("Parameter not found: ${CommonRestApi.paramRunId}")

        val executionId: LogicExecutionId = request
            .getSingle(CommonRestApi.paramExecutionId)
            ?.let { LogicExecutionId(it) }
            ?: return ExecutionResult.failure("Parameter not found: ${CommonRestApi.paramExecutionId}")

        val runExecutionParams = RequestParams.of(
            ReportConventions.paramAction to ReportConventions.actionOutputInfoOnline,
            CommonRestApi.paramRunId to runId.value,
            CommonRestApi.paramExecutionId to executionId.value,
            OutputExploreSpec.previewStartKey to output.explore.previewStartZeroBased().toString(),
            OutputExploreSpec.previewRowCountKey to output.explore.previewCount.toString(),
        )

        val pivotValueParams = analysis.pivot.values.asRequest()

        val combinedParams = runExecutionParams.addAll(pivotValueParams)

        return serverLogicController.request(
            runId,
            executionId,
            ExecutionRequest(combinedParams, null))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun actionValidateFormulas(): ExecutionResult {
        val reportRunContext = reportRunContext()
            ?: return ExecutionFailure("Missing run")

        val pluginCoordinates = reportRunContext.datasetInfo.items.map { it.processorPluginCoordinate }.toSet()
        val classLoaderHandle = definitionRepository
            .classLoaderHandle(pluginCoordinates, ClassLoaderUtils.dynamicParentClassLoader())

        val dataType = input.selection.dataType
        val flatHeaderListing = reportRunContext.datasetInfo.headerSuperset()

        return classLoaderHandle.use {
            val errors: Map<String, String> = reportRunContext.formula
                .formulas
                .mapValues { formula ->
                    calculatedColumnEval.validate(
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
            ReportConventions.paramAction to ReportConventions.actionSummaryOnline,
            CommonRestApi.paramRunId to runId.value,
            CommonRestApi.paramExecutionId to executionId.value
        )

        return serverLogicController.request(
            runId,
            executionId,
            ExecutionRequest(runExecutionParams, null))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Internal (not private): the engine-side ReportLogicCompiler reuses this spec -> run-context derivation
    // when translating the Report document into a ReportLogic.
    internal fun reportRunContext(): ReportRunContext? {
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

        val runDir = reportWorkPool.resolveRunDir(reportRunSignature, reportDir)

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
            val processorDefinitionMetadata = definitionRepository.metadata(pluginCoordinate)
                ?: return null

            val dataEncoding = ReportUtils.encodingWithMetadata(inputDataSpec, processorDefinitionMetadata)

            val flatDataLocation = FlatDataLocation(
                dataLocation, dataEncoding)

            val cachedHeaderListing = columnListingAction.cachedHeaderListing(
                dataLocation, pluginCoordinate)

            val headerListing = cachedHeaderListing
                ?: run {
                    val classLoaderHandle = definitionRepository
                        .classLoaderHandle(setOf(pluginCoordinate), ClassLoaderUtils.dynamicParentClassLoader())

                    classLoaderHandle.use {
                        val processorDefinition = definitionRepository.define(
                            pluginCoordinate, it)

                        columnListingAction.headerListing(
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
        val inputHeaderListing: HeaderListing = datasetInfo.headerSuperset()
        val calculatedHeaderListing = HeaderListing.ofUnique(formula.formulas.keys.toList())
//        val inputAndCalculatedColumnNames = inputColumnNames.append(calculatedColumnNames)

        if (analysis.type != AnalysisType.FlatData) {
            return AnalysisColumnInfo(
                FilteredHeaderListing.ofAll(inputHeaderListing),
                FilteredHeaderListing.ofAll(calculatedHeaderListing),
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

        fun include(headerLabelText: String): Boolean {
            val allow =
                allowPatterns.isEmpty() ||
                allowPatterns.any { it.matcher(headerLabelText).matches() }

            val exclude =
                excludePatterns.isNotEmpty() &&
                excludePatterns.any { it.matcher(headerLabelText).matches() }

            return allow && !exclude
        }

        val inputColumns = inputHeaderListing.values.associateWith { include(it.text) }
        val calculatedColumns = calculatedHeaderListing.values.associateWith { include(it.text) }

        return AnalysisColumnInfo(
            FilteredHeaderListing(HeaderLabelMap(inputColumns)),
            FilteredHeaderListing(HeaderLabelMap(calculatedColumns)),
            patternErrorOrNull(allowErrors),
            patternErrorOrNull(excludeErrors)
        )
    }
}
