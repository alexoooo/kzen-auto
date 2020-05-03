package tech.kzen.auto.server.objects.filter

import com.google.common.io.MoreFiles
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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path


@Reflect
object ColumnListing: DetachedAction {
    //-----------------------------------------------------------------------------------------------------------------
    private const val columnsCsvFilename = "columns.csv"


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: DetachedRequest): ExecutionResult {
        val input = request.parameters.get(FilterDocument.inputKey)
                ?: return ExecutionFailure("'input' required")

        val parsedPath = AutoJvmUtils.parsePath(input)
                ?: return ExecutionFailure("Invalid input: $input")

        val path = parsedPath.toAbsolutePath().normalize()

        if (! Files.isRegularFile(path)) {
            return ExecutionFailure("'input' not a regular file: $path")
        }

        val columnNames = columnNames(path)

        return ExecutionSuccess.ofValue(
                ExecutionValue.of(columnNames))
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun columnNames(inputPath: Path): List<String> {
        val inputIndexPath = FilterIndex.inputIndexPath(inputPath)
        val columnsFile = inputIndexPath.resolve(columnsCsvFilename)

        if (Files.exists(columnsFile)) {
            return withContext(Dispatchers.IO) {
                Files.newBufferedReader(columnsFile).use { reader ->
                    val parser = CSVFormat.DEFAULT.parse(reader)

                    // NB: skip header
                    parser.iterator().next()

                    parser.map { it[1] }
                }
            }
        }

        val firstLine= withContext(Dispatchers.IO) {
            @Suppress("UnstableApiUsage")
            MoreFiles
                .asCharSource(inputPath, StandardCharsets.UTF_8)
                .readFirstLine()
        } ?: return listOf()

        val columnNames = firstLine.split(",")

        val csvFile = "Number,Name\n" +
                columnNames
                    .withIndex()
                    .joinToString("\n") {
                        CSVFormat.DEFAULT.format(it.index, it.value)
                    }

        withContext(Dispatchers.IO) {
            Files.writeString(columnsFile, csvFile)
        }

        return columnNames
    }
}