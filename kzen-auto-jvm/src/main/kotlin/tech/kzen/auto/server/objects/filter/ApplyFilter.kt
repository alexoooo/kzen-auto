package tech.kzen.auto.server.objects.filter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.kzen.auto.common.objects.document.filter.FilterDocument
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.util.AutoJvmUtils
import tech.kzen.lib.common.reflect.Reflect
import java.nio.file.Files


@Reflect
object ApplyFilter: DetachedAction {
    override suspend fun execute(request: DetachedRequest): ExecutionResult {
        val input = request.parameters.get(FilterDocument.inputKey)
                ?: return ExecutionFailure("'input' required")

        val output = request.parameters.get(FilterDocument.outputKey)
                ?: return ExecutionFailure("'output' required")

        val parsedInputPath = AutoJvmUtils.parsePath(input)
                ?: return ExecutionFailure("Invalid input: $input")

        val parsedOutputPath = AutoJvmUtils.parsePath(output)
                ?: return ExecutionFailure("Invalid output: $output")

        val inputPath = parsedInputPath.toAbsolutePath().normalize()
        if (! Files.isRegularFile(inputPath)) {
            return ExecutionFailure("'input' not a regular file: $inputPath")
        }

        val outputPath = parsedOutputPath.toAbsolutePath().normalize()

        val lineCount = withContext(Dispatchers.IO) {
            Files.lines(inputPath).use { it.count() }
        }

        if (! Files.isDirectory(outputPath.parent)) {
            withContext(Dispatchers.IO) {
                Files.createDirectories(outputPath.parent)
            }
        }

        withContext(Dispatchers.IO) {
            Files.writeString(outputPath, lineCount.toString())
        }

        return ExecutionSuccess.ofValue(ExecutionValue.of(outputPath.toString()))
    }
}