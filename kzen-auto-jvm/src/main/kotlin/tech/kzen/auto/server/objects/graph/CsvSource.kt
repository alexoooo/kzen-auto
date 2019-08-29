package tech.kzen.auto.server.objects.graph

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeError
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import java.nio.file.Files
import java.nio.file.Paths


@Suppress("unused")
class CsvSource(
        var filePath: String
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        val path = Paths.get(filePath)

        if (! Files.exists(path)) {
            return ImperativeError("File not found")
        }

        val lineCount: Long =
                Files.lines(path).use { it.count() }

        val value = ExecutionValue.of("Line count: $lineCount")

        return ImperativeSuccess(value, NullExecutionValue)
    }
}