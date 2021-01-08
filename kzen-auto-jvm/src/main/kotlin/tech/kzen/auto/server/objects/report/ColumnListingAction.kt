package tech.kzen.auto.server.objects.report

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.read.RecordLineReader
import tech.kzen.auto.server.objects.report.pipeline.input.read.ReportStreamReader
import java.nio.file.Files
import java.nio.file.Path


class ColumnListingAction(
    private val filterIndex: FilterIndex
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val columnsCsvFilename = "columns.csv"
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun columnNamesMerge(inputPaths: List<Path>): List<String> {
        val builder = LinkedHashSet<String>()
        for (inputPath in inputPaths) {
            val columns = columnNames(inputPath)
            builder.addAll(columns)
        }
        return builder.toList()
    }


    suspend fun columnNames(inputPath: Path): List<String> {
        val inputIndexPath = filterIndex.inputIndexPath(inputPath)
        val columnsFile = inputIndexPath.resolve(columnsCsvFilename)

        if (Files.exists(columnsFile)) {
            val text = withContext(Dispatchers.IO) {
                Files.readString(columnsFile, Charsets.UTF_8)
            }

            return RecordLineReader
                .csvLines(text)
                .drop(1)
                .map { it.getString(1) }
        }

        val columnNames = ReportStreamReader.readHeaderLine(inputPath)

        val csvBody = columnNames
            .withIndex()
            .joinToString("\n") {
                RecordItemBuffer.of(listOf(it.index.toString(), it.value)).toCsv()
            }

        val csvFile = "Number,Name\n$csvBody"

        withContext(Dispatchers.IO) {
            Files.writeString(columnsFile, csvFile)
        }

        return columnNames
    }
}