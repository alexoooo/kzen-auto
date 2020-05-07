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
import java.nio.file.Files
import java.nio.file.Path


@Reflect
class FilterDocument(
        val input: String,
        val output: String,
        val criteria: CriteriaSpec
):
    DocumentArchetype(),
    DetachedAction
{
    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: DetachedRequest): ExecutionResult {
        val action = request.parameters.get(FilterConventions.actionParameter)
            ?: return ExecutionFailure("'${FilterConventions.actionParameter}' expected")

        val inputPath = inputPath()
            ?: return ExecutionFailure("Please provide a valid input path")

        return when (action) {
            FilterConventions.actionColumns ->
                actionColumnListing(inputPath)

            FilterConventions.actionSummary ->
                actionColumnSummary(inputPath, request)

            FilterConventions.actionApply ->
                actionApplyFilter(inputPath)

            FilterConventions.actionFiles ->
                TODO()

            else ->
                return ExecutionFailure("Unknown action: $action")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun inputPath(): Path? {
        val parsedInputPath = AutoJvmUtils.parsePath(input)
            ?: return null

        val inputPath = parsedInputPath.toAbsolutePath().normalize()

        if (! Files.isRegularFile(inputPath)) {
            return null
        }

        return inputPath
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun actionColumnListing(inputPath: Path): ExecutionResult {
        val columnNames = ColumnListing.columnNames(inputPath)
        return ExecutionSuccess.ofValue(
            ExecutionValue.of(columnNames))
    }


    private suspend fun actionColumnSummary(
        inputPath: Path,
        request: DetachedRequest
    ): ExecutionResult {
        val columnName = request.parameters.get(FilterConventions.columnKey)
            ?: return ExecutionFailure("'${FilterConventions.columnKey}' required")

        val summary = ColumnSummary.getValueSummary(inputPath, columnName)

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(summary.toCollection()))
    }


    private suspend fun actionApplyFilter(
        inputPath: Path
    ): ExecutionResult {
        val parsedOutputPath = AutoJvmUtils.parsePath(output)
            ?: return ExecutionFailure("Invalid output: $output")

        val outputPath = parsedOutputPath.toAbsolutePath().normalize()

        return ApplyFilter.applyFilter(inputPath, outputPath)
    }
}