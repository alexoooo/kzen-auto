package tech.kzen.auto.server.objects.report

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.PreviewSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputDataSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.paradigm.task.api.ManagedTask
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.api.TaskRun
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationJvm.normalize
import tech.kzen.auto.server.objects.plugin.PluginUtils.asCommon
import tech.kzen.auto.server.objects.plugin.PluginUtils.asPluginCoordinate
import tech.kzen.auto.server.objects.report.group.GroupPattern
import tech.kzen.auto.server.objects.report.model.ReportRunContext
import tech.kzen.auto.server.objects.report.pipeline.input.connect.file.FileFlatDataSource
import tech.kzen.auto.server.objects.report.pipeline.input.model.data.DatasetInfo
import tech.kzen.auto.server.objects.report.pipeline.input.model.data.FlatDataHeaderDefinition
import tech.kzen.auto.server.objects.report.pipeline.input.model.data.FlatDataInfo
import tech.kzen.auto.server.objects.report.pipeline.input.model.data.FlatDataLocation
import tech.kzen.auto.server.paradigm.detached.DetachedDownloadAction
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.awt.geom.IllegalPathStateException
import java.nio.file.Path
import java.nio.file.Paths


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
    ManagedTask
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ReportDocument::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: DetachedRequest): ExecutionResult {
        val action = request.parameters.get(ReportConventions.actionParameter)
            ?: return ExecutionFailure("'${ReportConventions.actionParameter}' expected")

        return when (action) {
            ReportConventions.actionBrowseFiles ->
                actionBrowseFiles()

            ReportConventions.actionDataTypes ->
                actionDataTypes()

            ReportConventions.actionTypeFormats ->
                actionTypeFormats()

            ReportConventions.actionDefaultFormat ->
                actionDefaultFormat(request)

            ReportConventions.actionInputInfo ->
                actionInputInfo()

            ReportConventions.actionListColumns ->
                actionColumnListing()

            ReportConventions.actionValidateFormulas ->
                actionValidateFormulas()

            ReportConventions.actionSummaryLookup ->
                actionColumnSummaryLookup()

            ReportConventions.actionLookupOutput ->
                actionLookupOutput()

            ReportConventions.actionSave ->
                actionSave()

            ReportConventions.actionReset ->
                actionReset()

            else ->
                return ExecutionFailure("Unknown action: $action")
        }
    }


    override suspend fun executeDownload(request: DetachedRequest): ExecutionDownloadResult {
        return actionDownload()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun datasetInfo(): DatasetInfo? {
        val groupPattern = GroupPattern.parse(input.selection.groupBy)
            ?: GroupPattern.empty

        val items = mutableListOf<FlatDataInfo>()
        for (inputDataSpec in input.selection.locations) {
            val dataLocation = inputDataSpec.location

            val processorDefinitionMetadata = ServerContext.definitionRepository.metadata(
                inputDataSpec.processorDefinitionCoordinate.asPluginCoordinate())
                ?: return null

            val dataEncoding = ReportUtils.encodingWithMetadata(inputDataSpec, processorDefinitionMetadata)

            val flatDataLocation = FlatDataLocation(
                dataLocation, dataEncoding)

            val pluginCoordinate = inputDataSpec.processorDefinitionCoordinate.asPluginCoordinate()
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


    private fun runContext(): ReportRunContext? {
        val datasetInfo = datasetInfo()
            ?: return null

        val dataType = input.selection.dataType

        return ReportRunContext(
            dataType, datasetInfo, formula, previewAll, filter, previewFiltered, analysis)
    }


    private fun runDir(runContext: ReportRunContext): Path {
        val reportDir =
            try {
                Paths.get(output.explore.workPath)
            }
            catch (e: IllegalPathStateException) {
                ReportWorkPool.defaultReportDir
            }

        val runSignature = runContext.toSignature()
        return ServerContext.reportWorkPool.resolveRunDir(runSignature, reportDir)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun actionBrowseFiles(): ExecutionResult {
        val absoluteDir = browseDir()
        val inputPaths = ServerContext.fileListingAction.scanInfo(
            input.browser.directory, input.browser.filter)

        val inputInfo = InputBrowserInfo(
            absoluteDir, inputPaths)

        return ExecutionSuccess.ofValue(ExecutionValue.of(
            inputInfo.asCollection()))
    }


    private fun actionInputInfo(): ExecutionResult {
        val groupPattern = GroupPattern.parse(input.selection.groupBy)
            ?: return ExecutionFailure("Group By pattern error: ${input.selection.groupBy}")

        val inputSelectionInfo = ServerContext.fileListingAction
            .selectionInfo(input.selection, groupPattern)
            .sorted()

        return ExecutionSuccess.ofValue(ExecutionValue.of(
            inputSelectionInfo.asCollection()))
    }


    private fun browseDir(): DataLocation {
        return input.browser.directory.normalize()
    }


    private fun actionColumnListing(): ExecutionResult {
        val inputPaths = datasetInfo()
            ?: return ExecutionFailure("Please provide a valid inputs")

        val columnNames = inputPaths.headerSuperset().values
        return ExecutionSuccess.ofValue(
            ExecutionValue.of(columnNames))
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


    private fun actionDefaultFormat(request: DetachedRequest): ExecutionResult {
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


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun actionColumnSummaryLookup(): ExecutionResult {
        val runSpec = runContext()
            ?: return ExecutionFailure("Missing run")

        val runDir = runDir(runSpec)

        return ServerContext.reportRunAction.summaryView(
            selfLocation, runSpec, runDir)
    }


//    private fun dataTypeProvider(): ClassLoaderHandle {
//        val modelType = input.selection.dataType
//
//        return ServerContext
//            .definitionRepository
//            .classLoaderHandle(modelType)
//    }


    private fun actionValidateFormulas(): ExecutionResult {
        val runSpec = runContext()
            ?: return ExecutionFailure("Missing run")

        val pluginCoordinates = runSpec.datasetInfo.items.map { it.processorPluginCoordinate }.toSet()
        val classLoaderHandle = ServerContext.definitionRepository
            .classLoaderHandle(pluginCoordinates, ClassLoader.getSystemClassLoader())

        val dataType = input.selection.dataType

        return classLoaderHandle.use {
            ServerContext.reportRunAction.formulaValidation(
                runSpec.formula, runSpec.datasetInfo.headerSuperset(), dataType, it.classLoader)
        }
    }


    private suspend fun actionLookupOutput(): ExecutionResult {
        val runSpec = runContext()
            ?: return ExecutionFailure("Missing run")

        val runDir = runDir(runSpec)

        return ServerContext.reportRunAction.outputPreview(
            selfLocation, runSpec, runDir, output.explore)
    }


    private fun actionSave(): ExecutionResult {
        val runSpec = runContext()
            ?: return ExecutionFailure("Missing run")

        val runDir = runDir(runSpec)

        return ServerContext.reportRunAction.outputSave(
            runSpec, runDir, output.explore)
    }


    private fun actionReset(): ExecutionResult {
        val runSpec = runContext()
            ?: return ExecutionFailure("Missing run")

        val runDir = runDir(runSpec)

        return ServerContext.reportRunAction.delete(runDir)
    }


    private fun actionDownload(): ExecutionDownloadResult {
        val runSpec = runContext()
            ?: error("Missing run")

        val runDir = runDir(runSpec)

        return ServerContext.reportRunAction.outputDownload(
            runSpec, runDir, output.explore, selfLocation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun start(request: DetachedRequest, handle: TaskHandle): TaskRun? {
        val action = request.parameters.get(ReportConventions.actionParameter)
        if (action == null) {
            handle.complete(ExecutionFailure(
                "'${ReportConventions.actionParameter}' expected"))
            return null
        }

        val runContext = runContext()

        if (runContext == null) {
            handle.complete(ExecutionFailure(
                "Please provide a valid input path"))
            return null
        }

        return when (action) {
            ReportConventions.actionRunTask -> {
                try {
                    actionRunReport(runContext, handle)
                }
                catch (e: Exception) {
                    logger.warn("Unable to start", e)
                    handle.complete(ExecutionFailure.ofException(
                        "Unable to start - ", e))
                    null
                }
            }

            else -> {
                handle.complete(ExecutionFailure(
                    "Unknown action: $action"))
                null
            }
        }
    }


    private fun actionRunReport(
        runContext: ReportRunContext,
        handle: TaskHandle
    ): TaskRun {
        val runDir = runDir(runContext)

        ServerContext.reportWorkPool.prepareRunDir(runDir)

//        val dataTypeProvider = dataTypeProvider()

        return ServerContext.reportRunAction.startReport(
            /*dataTypeProvider,*/ runContext, runDir, handle)
    }
}