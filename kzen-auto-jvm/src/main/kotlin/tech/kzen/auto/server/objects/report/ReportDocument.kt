package tech.kzen.auto.server.objects.report

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.objects.document.report.spec.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.OutputSpec
import tech.kzen.auto.common.objects.document.report.spec.PivotSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputDataSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
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
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
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
    private val filter: FilterSpec,
    private val pivot: PivotSpec,
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
        val items = mutableListOf<FlatDataInfo>()
        for (inputDataSpec in input.selection.locations) {
            val dataLocation = inputDataSpec.location

            val processorDefinitionMetadata = ServerContext.definitionRepository.metadata(
                inputDataSpec.processorDefinitionCoordinate.asPluginCoordinate())
                ?: return null

            val dataEncoding = ReportUtils.encodingWithMetadata(inputDataSpec, processorDefinitionMetadata)

            val dataLocationInfo = FlatDataLocation(
                dataLocation, dataEncoding)

            val pluginCoordinate = inputDataSpec.processorDefinitionCoordinate.asPluginCoordinate()
            val cachedHeaderListing = ServerContext.columnListingAction.cachedHeaderListing(
                dataLocation, pluginCoordinate)

            val headerListing = cachedHeaderListing
                ?: run {
                    val processorDefinition = ServerContext.definitionRepository.define(pluginCoordinate)

                    processorDefinition.use {
                        ServerContext.columnListingAction.headerListing(
                            FlatDataHeaderDefinition(
                                dataLocationInfo,
                                FileFlatDataSource(),
                                it),
                            pluginCoordinate
                        )
                    }
                }

            items.add(FlatDataInfo(dataLocationInfo, headerListing, pluginCoordinate))
        }
        return DatasetInfo(items)
    }


    private fun runSpec(): ReportRunSpec? {
        val datasetInfo = datasetInfo()
            ?: return null

//        val columnNames = ServerContext.columnListingAction.columnNames(datasetInfo)

        return ReportRunSpec(
            datasetInfo, formula, filter, pivot)
    }


    private fun runDir(runSpec: ReportRunSpec): Path {
        val reportDir =
            try {
                Paths.get(output.workPath)
            }
            catch (e: IllegalPathStateException) {
                ReportWorkPool.defaultReportDir
            }

        val runSignature = runSpec.toSignature()
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
        val inputSelectionInfo = ServerContext.fileListingAction.selectionInfo(input.selection)

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
        val runSpec = runSpec()
            ?: return ExecutionFailure("Missing run")

        val runDir = runDir(runSpec)

        return ServerContext.reportRunAction.summaryView(
            selfLocation, runSpec, runDir)
    }


    private suspend fun actionValidateFormulas(): ExecutionResult {
        val runSpec = runSpec()
            ?: return ExecutionFailure("Missing run")

        return ServerContext.reportRunAction.formulaValidation(
            runSpec.toFormulaSignature())
    }


    private suspend fun actionLookupOutput(): ExecutionResult {
        val runSpec = runSpec()
            ?: return ExecutionFailure("Missing run")

        val runDir = runDir(runSpec)

        return ServerContext.reportRunAction.outputPreview(
            selfLocation, runSpec, runDir, output)
    }


    private fun actionSave(): ExecutionResult {
        val runSpec = runSpec()
            ?: return ExecutionFailure("Missing run")

        val runDir = runDir(runSpec)

        return ServerContext.reportRunAction.outputSave(
            runSpec, runDir, output)
    }


    private fun actionReset(): ExecutionResult {
        val runSpec = runSpec()
            ?: return ExecutionFailure("Missing run")

        val runDir = runDir(runSpec)

        return ServerContext.reportRunAction.delete(runDir)
    }


    private fun actionDownload(): ExecutionDownloadResult {
        val runSpec = runSpec()
            ?: error("Missing run")

        val runDir = runDir(runSpec)

        return ServerContext.reportRunAction.outputDownload(
            runSpec, runDir, output, selfLocation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun start(request: DetachedRequest, handle: TaskHandle): TaskRun? {
        val action = request.parameters.get(ReportConventions.actionParameter)
        if (action == null) {
            handle.complete(ExecutionFailure(
                "'${ReportConventions.actionParameter}' expected"))
            return null
        }

        val runSpec = runSpec()

        if (runSpec == null) {
            handle.complete(ExecutionFailure(
                "Please provide a valid input path"))
            return null
        }

        return when (action) {
            ReportConventions.actionRunTask -> {
                try {
                    actionRunReport(runSpec, handle)
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
        runSpec: ReportRunSpec,
        handle: TaskHandle
    ): TaskRun {
        val runDir = runDir(runSpec)

        ServerContext.reportWorkPool.prepareRunDir(runDir)

        return ServerContext.reportRunAction.startReport(
            runSpec, runDir, handle)
    }
}