package tech.kzen.auto.server.objects.filter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import java.nio.file.Files
import java.nio.file.Path


object ApplyFilter {
    suspend fun applyFilter(
        inputPath: Path,
        outputPath: Path
    ): ExecutionResult {
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