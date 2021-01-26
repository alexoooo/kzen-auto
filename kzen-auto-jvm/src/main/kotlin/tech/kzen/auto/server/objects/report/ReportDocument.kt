package tech.kzen.auto.server.objects.report

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.InputInfo
import tech.kzen.auto.common.objects.document.report.spec.*
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.paradigm.task.api.ManagedTask
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.api.TaskRun
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import tech.kzen.auto.server.paradigm.detached.DetachedDownloadAction
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.auto.util.AutoJvmUtils
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
    override suspend fun execute(request: DetachedRequest): ExecutionResult {
        val action = request.parameters.get(ReportConventions.actionParameter)
            ?: return ExecutionFailure("'${ReportConventions.actionParameter}' expected")

        return when (action) {
            ReportConventions.actionBrowseFiles ->
                actionBrowseFiles()

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
    private suspend fun inputPaths(): List<Path>? {
        return ServerContext.fileListingAction.list(input.directory)
    }


    private suspend fun runSpec(): ReportRunSpec? {
        val inputPaths = inputPaths()
            ?: return null

        val columnNames = ServerContext.columnListingAction.columnNamesMerge(inputPaths)

        return ReportRunSpec(
            inputPaths, columnNames, formula, filter, pivot)
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
        val inputPaths = ServerContext.fileListingAction.listInfo(input.directory)

        val inputInfo = InputInfo(
            absoluteDir, inputPaths)

        return ExecutionSuccess.ofValue(ExecutionValue.of(
            inputInfo.toCollection()))
    }


    private fun actionInputInfo(): ExecutionResult {
        val absoluteDir = browseDir()
        val selectedPaths = ServerContext.fileListingAction.listInfo(input.selected)

        val inputInfo = InputInfo(
            absoluteDir, selectedPaths)

        return ExecutionSuccess.ofValue(ExecutionValue.of(
            inputInfo.toCollection()))
    }


    private fun browseDir(): String {
        return AutoJvmUtils
            .parsePath(input.directory)
            ?.toAbsolutePath()
            ?.normalize()
            ?.toString()
            ?: input.directory
    }


    private suspend fun actionColumnListing(): ExecutionResult {
        val inputPaths = inputPaths()
            ?: return ExecutionFailure("Please provide a valid input path")

        val columnNames = ServerContext.columnListingAction.columnNamesMerge(inputPaths)
        return ExecutionSuccess.ofValue(
            ExecutionValue.of(columnNames))
    }


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


    private suspend fun actionSave(): ExecutionResult {
        val runSpec = runSpec()
            ?: return ExecutionFailure("Missing run")

        val runDir = runDir(runSpec)

        return ServerContext.reportRunAction.outputSave(
            runSpec, runDir, output)
    }


    private suspend fun actionReset(): ExecutionResult {
        val runSpec = runSpec()
            ?: return ExecutionFailure("Missing run")

        val runDir = runDir(runSpec)

        return ServerContext.reportRunAction.delete(runDir)
    }


    private suspend fun actionDownload(): ExecutionDownloadResult {
        val runSpec = runSpec()
            ?: error("Missing run")

        val runDir = runDir(runSpec)

        return ServerContext.reportRunAction.outputDownload(
            runSpec, runDir, output)
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

        when (action) {
//            ProcessConventions.actionSummaryTask -> {
//                val runSignature = runSpec.toSignature()
//                actionColumnSummaryAsync(runSignature, handle)
//            }

            ReportConventions.actionRunTask -> {
                return actionRunReport(runSpec, handle)
            }

            else -> {
                handle.complete(ExecutionFailure(
                    "Unknown action: $action"))
                return null
            }
        }
    }


    private suspend fun actionRunReport(
        runSpec: ReportRunSpec,
        handle: TaskHandle
    ): TaskRun {
        val runDir = runDir(runSpec)

        ServerContext.reportWorkPool.prepareRunDir(runDir)

        return ServerContext.reportRunAction.startReport(
            runSpec, runDir, handle)
    }
}