package tech.kzen.auto.server.objects.query

import tech.kzen.auto.common.paradigm.imperative.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionResult
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionValue
import tech.kzen.auto.common.paradigm.imperative.model.NullExecutionValue
import java.nio.file.Files
import java.nio.file.Paths


@Suppress("unused")
class CsvSource(
        var filePath: String
): ExecutionAction {
    override suspend fun perform(): ExecutionResult {
        val lineCount = Files.readAllLines(Paths.get(filePath)).size
        return ExecutionSuccess(
                ExecutionValue.of(lineCount),
                NullExecutionValue)
    }
}