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
import tech.kzen.auto.util.AutoJvmUtils
import tech.kzen.lib.common.reflect.Reflect
import java.nio.file.Path


@Reflect
class ProcessDocument(
    private val input: String,
    private val filter: FilterSpec,
    private val pivot: PivotSpec,
    private val output: String
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

            ProcessConventions.actionLookupOutput ->
                actionLookupOutput()

            ProcessConventions.actionSummaryLookup ->
                actionColumnSummaryLookup()

//            FilterConventions.actionFilter ->
//                actionApplyFilterAsync(inputPaths)

            else ->
                return ExecutionFailure("Unknown action: $action")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun inputPaths(): List<Path>? {
        return FileListingAction.list(input)
//        val parsedInputPath = AutoJvmUtils.parsePath(input)
//            ?: return null
//
//        val inputPath = parsedInputPath.toAbsolutePath().normalize()
//
//        if (! Files.isRegularFile(inputPath)) {
//            return null
//        }
//
//        return inputPath
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun actionColumnListing(): ExecutionResult {
        val inputPaths = inputPaths()
            ?: return ExecutionFailure("Please provide a valid input path")

        val columnNames = ColumnListingAction.columnNamesMerge(inputPaths)
        return ExecutionSuccess.ofValue(
            ExecutionValue.of(columnNames))
    }


    private suspend fun actionColumnSummaryLookup(): ExecutionResult {
        val inputPaths = inputPaths()
            ?: return ExecutionFailure("Please provide a valid input path")

        val columnNames = ColumnListingAction.columnNamesMerge(inputPaths)
        return ColumnSummaryAction.lookupSummary(
            inputPaths, columnNames)
    }


    private suspend fun actionLookupOutput(): ExecutionResult {
        val parsedOutputPath = AutoJvmUtils.parsePath(output)
            ?: return ExecutionFailure("Invalid output: $output")

        val outputPath = parsedOutputPath.toAbsolutePath().normalize()

        return ApplyProcessAction.lookupOutput(outputPath)
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

        val inputPaths = inputPaths()
        if (inputPaths == null) {
            handle.complete(ExecutionFailure(
                "Please provide a valid input path"))
            return
        }

        val columnNames = ColumnListingAction.columnNamesMerge(inputPaths)

        when (action) {
//            FilterConventions.actionFiles -> {
//                val result = actionListFiles(inputPaths)
//                handle.complete(result)
//            }
//
//            FilterConventions.actionColumns -> {
//                val result = actionColumnListing(inputPaths)
//                handle.complete(result)
//            }

            ProcessConventions.actionSummaryTask -> {
//                val result = actionColumnSummary(inputPaths, request)
//                handle.complete(result)
                actionColumnSummaryAsync(inputPaths, columnNames, /*request,*/ handle)
            }

            ProcessConventions.actionFilterTask -> {
                actionApplyFilterAsync(
                    inputPaths, columnNames, handle)
//                handle.complete(result)
            }

            else -> {
                handle.complete(ExecutionFailure(
                    "Unknown action: $action"))
            }
        }
    }


    private suspend fun actionColumnSummaryAsync(
        inputPaths: List<Path>,
        columnNames: List<String>,
//        request: DetachedRequest,
        handle: TaskHandle
    ) {
//        val columnName = request.parameters.get(FilterConventions.columnKey)
//        if (columnName == null) {
//            handle.complete(ExecutionFailure(
//                "'${FilterConventions.columnKey}' required"))
//            return
//        }

        ColumnSummaryAction.summarizeAllAsync(
            inputPaths, columnNames, handle)
    }


    private suspend fun actionApplyFilterAsync(
        inputPaths: List<Path>,
        columnNames: List<String>,
        handle: TaskHandle
    ) {
        val parsedOutputPath = AutoJvmUtils.parsePath(output)
        if (parsedOutputPath == null) {
            handle.complete(ExecutionFailure(
                "Invalid output location: $output"))
            return
        }

        val outputPath = parsedOutputPath.toAbsolutePath().normalize()

        ApplyProcessAction.applyProcessAsync(
            inputPaths, columnNames, outputPath, filter, pivot, handle)
    }
}