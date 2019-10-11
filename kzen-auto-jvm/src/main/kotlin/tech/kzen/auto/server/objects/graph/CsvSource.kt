package tech.kzen.auto.server.objects.graph

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import java.nio.file.Files
import java.nio.file.Paths


@Suppress("unused")
class CsvSource(
        var filePath: String
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ExecutionResult {
        val path = Paths.get(filePath)

        if (! Files.exists(path)) {
            return ExecutionFailure("File not found")
        }

        val lineCount: Long =
                Files.lines(path).use { it.count() }

        val value = ExecutionValue.of("Line count: $lineCount")

        return ExecutionSuccess(value, NullExecutionValue)
    }
}