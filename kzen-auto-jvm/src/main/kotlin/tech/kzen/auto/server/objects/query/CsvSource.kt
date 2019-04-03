package tech.kzen.auto.server.objects.query

import tech.kzen.auto.common.paradigm.imperative.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.*
import java.nio.file.Files
import java.nio.file.Paths


@Suppress("unused")
class CsvSource(
        var filePath: String
): ExecutionAction {
    override suspend fun perform(): ExecutionResult {
        val path = Paths.get(filePath)

        if (! Files.exists(path)) {
            return ExecutionError("File not found")
        }

        val lineCount: Long =
                Files.lines(path).use { it.count() }

        val value = ExecutionValue.of("Line count: $lineCount")

        return ExecutionSuccess(value, NullExecutionValue)
    }
}