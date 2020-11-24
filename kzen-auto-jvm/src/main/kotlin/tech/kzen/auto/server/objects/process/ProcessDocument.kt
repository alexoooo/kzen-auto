package tech.kzen.auto.server.objects.process

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.process.FilterSpec
import tech.kzen.auto.common.objects.document.process.OutputSpec
import tech.kzen.auto.common.objects.document.process.PivotSpec
import tech.kzen.auto.common.objects.document.process.ProcessConventions
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.paradigm.task.api.ManagedTask
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.api.TaskRun
import tech.kzen.auto.server.objects.process.model.ProcessRunSpec
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.nio.file.Path


@Reflect
class ProcessDocument(
    private val input: String,
    private val filter: FilterSpec,
    private val pivot: PivotSpec,
    private val output: OutputSpec,
    private val selfLocation: ObjectLocation
):
    DocumentArchetype(),
    DetachedAction,
    ManagedTask
{
    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: DetachedRequest): ExecutionResult {
        val action = request.parameters.get(ProcessConventions.actionParameter)
            ?: return ExecutionFailure("'${ProcessConventions.actionParameter}' expected")

        return when (action) {
            ProcessConventions.actionListFiles ->
                actionListFiles()

            ProcessConventions.actionListColumns ->
                actionColumnListing()

            ProcessConventions.actionSummaryLookup ->
                actionColumnSummaryLookup()

            ProcessConventions.actionLookupOutput ->
                actionLookupOutput()

            ProcessConventions.actionSave ->
                actionSave()

            else ->
                return ExecutionFailure("Unknown action: $action")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun inputPaths(): List<Path>? {
        return FileListingAction.list(input)
    }


    private suspend fun runSpec(): ProcessRunSpec? {
        val inputPaths = inputPaths()
            ?: return null

        val columnNames = ColumnListingAction.columnNamesMerge(inputPaths)

        return ProcessRunSpec(
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
            ?: return ExecutionFailure("Please provide a valid input path")

        val runSignature = runSpec.toSignature()

        return ColumnSummaryAction.lookupSummary(runSignature)
    }


    private suspend fun actionLookupOutput(): ExecutionResult {
        val runSpec = runSpec()
            ?: return ExecutionFailure("Missing run")

        val runSignature = runSpec.toSignature()
        val runDir = ProcessWorkPool.resolveRunDir(runSignature)

        return ReportRunAction.lookupOutput(
            selfLocation, runSpec, runDir, output)
    }


    private suspend fun actionSave(): ExecutionResult {
        val runSpec = runSpec()
            ?: return ExecutionFailure("Missing run")

        val runSignature = runSpec.toSignature()
        val runDir = ProcessWorkPool.resolveRunDir(runSignature)

        return ReportRunAction.saveOutput(
            runSpec, runDir, output)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun start(request: DetachedRequest, handle: TaskHandle): TaskRun? {
        val action = request.parameters.get(ProcessConventions.actionParameter)
        if (action == null) {
            handle.complete(ExecutionFailure(
                "'${ProcessConventions.actionParameter}' expected"))
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

            ProcessConventions.actionRunTask -> {
                actionRunReport(runSpec, handle)
            }

            else -> {
                handle.complete(ExecutionFailure(
                    "Unknown action: $action"))
            }
        }

        return null
    }


//    private suspend fun actionColumnSummaryAsync(
//        runSignature: ProcessRunSignature,
//        handle: TaskHandle
//    ) {
//        ColumnSummaryAction.summarizeAllAsync(
//            runSignature, handle)
//    }


    private suspend fun actionRunReport(
        runSpec: ProcessRunSpec,
        handle: TaskHandle
    ): TaskRun {
        val runSignature = runSpec.toSignature()
        val runDir = ProcessWorkPool.getOrPrepareRunDir(runSignature)

        return ReportRunAction.applyProcessAsync(
            runSpec, runDir, handle)
    }
}