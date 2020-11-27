package tech.kzen.auto.server.objects.report

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.spec.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.OutputSpec
import tech.kzen.auto.common.objects.document.report.spec.PivotSpec
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
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.nio.file.Path


@Reflect
class ReportDocument(
    private val input: String,
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
            ReportConventions.actionListFiles ->
                actionListFiles()

            ReportConventions.actionListColumns ->
                actionColumnListing()

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
        return FileListingAction.list(input)
    }


    private suspend fun runSpec(): ReportRunSpec? {
        val inputPaths = inputPaths()
            ?: return null

        val columnNames = ColumnListingAction.columnNamesMerge(inputPaths)

        return ReportRunSpec(
            inputPaths, columnNames, filter, pivot)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun actionListFiles(): ExecutionResult {
        val inputPaths = inputPaths()
            ?: return ExecutionFailure("Please provide a valid input path")

        return ExecutionSuccess.ofValue(ExecutionValue.of(
            inputPaths.map { it.toString() }))
    }


    private suspend fun actionColumnListing(): ExecutionResult {
        val inputPaths = inputPaths()
            ?: return ExecutionFailure("Please provide a valid input path")

        val columnNames = ColumnListingAction.columnNamesMerge(inputPaths)
        return ExecutionSuccess.ofValue(
            ExecutionValue.of(columnNames))
    }


    private suspend fun actionColumnSummaryLookup(): ExecutionResult {
        val runSpec = runSpec()
            ?: return ExecutionFailure("Missing run")

        val runSignature = runSpec.toSignature()
        val runDir = ReportWorkPool.resolveRunDir(runSignature)

        return ReportRunAction.summaryView(
            selfLocation, runSpec, runDir)
    }


    private suspend fun actionLookupOutput(): ExecutionResult {
        val runSpec = runSpec()
            ?: return ExecutionFailure("Missing run")

        val runSignature = runSpec.toSignature()
        val runDir = ReportWorkPool.resolveRunDir(runSignature)

        return ReportRunAction.outputPreview(
            selfLocation, runSpec, runDir, output)
    }


    private suspend fun actionSave(): ExecutionResult {
        val runSpec = runSpec()
            ?: return ExecutionFailure("Missing run")

        val runSignature = runSpec.toSignature()
        val runDir = ReportWorkPool.resolveRunDir(runSignature)

        return ReportRunAction.outputSave(
            runSpec, runDir, output)
    }


    private suspend fun actionReset(): ExecutionResult {
        val runSpec = runSpec()
            ?: return ExecutionFailure("Missing run")

        val runSignature = runSpec.toSignature()
        val runDir = ReportWorkPool.resolveRunDir(runSignature)

        return ReportRunAction.delete(runDir)
    }


    private suspend fun actionDownload(): ExecutionDownloadResult {
        val runSpec = runSpec()
            ?: error("Missing run")

        val runSignature = runSpec.toSignature()
        val runDir = ReportWorkPool.resolveRunDir(runSignature)

        return ReportRunAction.outputDownload(
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
        val runSignature = runSpec.toSignature()
        val runDir = ReportWorkPool.getOrPrepareRunDir(runSignature)

        return ReportRunAction.startReport(
            runSpec, runDir, handle)
    }
}