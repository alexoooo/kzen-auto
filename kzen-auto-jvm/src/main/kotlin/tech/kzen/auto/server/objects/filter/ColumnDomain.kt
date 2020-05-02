package tech.kzen.auto.server.objects.filter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
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
object ColumnDomain: DetachedAction {
//    private val emptyDomain = ExecutionSuccess.ofValue(ExecutionValue.of(emptyList<String>()))


    override suspend fun execute(request: DetachedRequest): ExecutionResult {
        val input = request.parameters.get(FilterDocument.inputKey)
                ?: return ExecutionFailure("'${FilterDocument.inputKey}' required")

        val columnIndexValue = request.parameters.get(FilterDocument.indexKey)
                ?: return ExecutionFailure("'${FilterDocument.indexKey}' required")

        val columnIndex = columnIndexValue.toIntOrNull()
                ?:return ExecutionFailure("'${FilterDocument.indexKey}' not an int")

        val parsedPath = AutoJvmUtils.parsePath(input)
                ?: return ExecutionFailure("Invalid input: $input")

        val path = parsedPath.toAbsolutePath().normalize()

        if (! Files.isRegularFile(path)) {
            return ExecutionFailure("'input' not a regular file: $path")
        }

        val builder = mutableSetOf<String>()

        withContext(Dispatchers.IO) {
            Files.newBufferedReader(path).use {
                val csvParser = CSVFormat.DEFAULT.parse(it)
                for (record in csvParser) {
                    val value = record.get(columnIndex)
                    builder.add(value)
                }
            }
        }

        val summary = builder.size

        return ExecutionSuccess.ofValue(
                ExecutionValue.of(summary))
    }
}