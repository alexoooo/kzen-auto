package tech.kzen.auto.server.objects.filter

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.filter.CriteriaSpec
import tech.kzen.auto.common.objects.document.filter.FilterConventions
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.util.AutoJvmUtils
import tech.kzen.lib.common.reflect.Reflect
import java.nio.file.Path


@Reflect
class FilterDocument(
        private val input: String,
        private val output: String,
        private val criteria: CriteriaSpec
):
    DocumentArchetype(),
    DetachedAction
{
    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: DetachedRequest): ExecutionResult {
        val action = request.parameters.get(FilterConventions.actionParameter)
            ?: return ExecutionFailure("'${FilterConventions.actionParameter}' expected")

        val inputPaths = inputPaths()
            ?: return ExecutionFailure("Please provide a valid input path")

        return when (action) {
            FilterConventions.actionColumns ->
                actionColumnListing(inputPaths)

            FilterConventions.actionSummary ->
                actionColumnSummary(inputPaths, request)

            FilterConventions.actionApply ->
                actionApplyFilter(inputPaths)

            FilterConventions.actionFiles ->
                actionListFiles(inputPaths)

            else ->
                return ExecutionFailure("Unknown action: $action")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun inputPaths(): List<Path>? {
        return FileListing.list(input)
//
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
    private suspend fun actionColumnListing(inputPaths: List<Path>): ExecutionResult {
        val columnNames = ColumnListing.columnNamesMerge(inputPaths)
        return ExecutionSuccess.ofValue(
            ExecutionValue.of(columnNames))
    }


    private suspend fun actionColumnSummary(
        inputPaths: List<Path>,
        request: DetachedRequest
    ): ExecutionResult {
        val columnName = request.parameters.get(FilterConventions.columnKey)
            ?: return ExecutionFailure("'${FilterConventions.columnKey}' required")

        val summary = ColumnSummary.summarizeAll(
            inputPaths, columnName)

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(summary.toCollection()))
    }


    private suspend fun actionApplyFilter(
        inputPaths: List<Path>
    ): ExecutionResult {
        val parsedOutputPath = AutoJvmUtils.parsePath(output)
            ?: return ExecutionFailure("Invalid output: $output")

        val outputPath = parsedOutputPath.toAbsolutePath().normalize()

        val columnNames = ColumnListing.columnNamesMerge(inputPaths)

        return ApplyFilter.applyFilter(
            inputPaths, columnNames, outputPath, criteria)
    }


    private fun actionListFiles(
        inputPaths: List<Path>
    ): ExecutionResult {
        return ExecutionSuccess.ofValue(ExecutionValue.of(
            inputPaths.map { it.toString() }))
    }
}