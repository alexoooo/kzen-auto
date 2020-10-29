package tech.kzen.auto.server.objects.process

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.process.FilterSpec
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
import tech.kzen.auto.server.objects.process.model.ProcessRunSignature
import tech.kzen.auto.server.objects.process.model.ProcessRunSpec
import tech.kzen.lib.common.reflect.Reflect
import java.nio.file.Path


@Reflect
class ProcessDocument(
    private val input: String,
    private val filter: FilterSpec,
    private val pivot: PivotSpec/*,
    private val output: String*/
):
    DocumentArchetype(),
    DetachedAction,
    ManagedTask
{
    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        private val dataProcessDir = Path.of("data-process")
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: DetachedRequest): ExecutionResult {
        val action = request.parameters.get(ProcessConventions.actionParameter)
            ?: return ExecutionFailure("'${ProcessConventions.actionParameter}' expected")

        return when (action) {
            ProcessConventions.actionListFiles ->
                actionListFiles()

            ProcessConventions.actionListColumns ->
                actionColumnListing()

            ProcessConventions.actionLookupOutput ->
                actionLookupOutput(request)

            ProcessConventions.actionSummaryLookup ->
                actionColumnSummaryLookup()

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


    private suspend fun runDir(): Path? {
        val runSpec = runSpec()
            ?: return null

        val runSignature = runSpec.toSignature()

        return ProcessWorkPool.resolveRunDir(runSignature)
//        return runDir(runSignature)
    }


//    private fun runDir(runSignature: ProcessRunSignature): Path {
//        val tempName = runSignature.digest().asString()
//        val tempPath = dataProcessDir.resolve(tempName)
//        val workDir = WorkUtils.resolve(tempPath)
//        return workDir.toAbsolutePath().normalize()
//    }


    //-----------------------------------------------------------------------------------------------------------------
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


    private suspend fun actionLookupOutput(request: DetachedRequest): ExecutionResult {
        val runDir = runDir()
            ?: return ExecutionFailure("Missing run dir")

        return ApplyProcessAction.lookupOutput(runDir, request)
    }


    private suspend fun actionListFiles(): ExecutionResult {
        val inputPaths = inputPaths()
            ?: return ExecutionFailure("Please provide a valid input path")

        return ExecutionSuccess.ofValue(ExecutionValue.of(
            inputPaths.map { it.toString() }))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun start(request: DetachedRequest, handle: TaskHandle) {
        val action = request.parameters.get(ProcessConventions.actionParameter)
        if (action == null) {
            handle.complete(ExecutionFailure(
                "'${ProcessConventions.actionParameter}' expected"))
            return
        }

        val runSpec = runSpec()

        if (runSpec == null) {
            handle.complete(ExecutionFailure(
                "Please provide a valid input path"))
            return
        }

        when (action) {
            ProcessConventions.actionSummaryTask -> {
                val runSignature = runSpec.toSignature()
                actionColumnSummaryAsync(runSignature, handle)
            }

            ProcessConventions.actionFilterTask -> {
                actionApplyFilterAsync(runSpec, handle)
            }

            else -> {
                handle.complete(ExecutionFailure(
                    "Unknown action: $action"))
            }
        }
    }


    private suspend fun actionColumnSummaryAsync(
        runSignature: ProcessRunSignature,
        handle: TaskHandle
    ) {
        ColumnSummaryAction.summarizeAllAsync(
            runSignature, handle)
    }


    private suspend fun actionApplyFilterAsync(
        runSpec: ProcessRunSpec,
        handle: TaskHandle
    ) {
        val runSignature = runSpec.toSignature()
        val runDir = ProcessWorkPool.getOrPrepareRunDir(runSignature)

        ApplyProcessAction.applyProcessAsync(
            runSpec, runDir, handle)
    }
}