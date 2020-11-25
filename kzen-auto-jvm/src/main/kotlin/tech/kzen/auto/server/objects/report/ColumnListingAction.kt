package tech.kzen.auto.server.objects.report

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import tech.kzen.auto.server.objects.report.pipeline.ReportInput
import java.nio.file.Files
import java.nio.file.Path


object ColumnListingAction {
    //-----------------------------------------------------------------------------------------------------------------
    private const val columnsCsvFilename = "columns.csv"


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
        val inputIndexPath = FilterIndex.inputIndexPath(inputPath)
        val columnsFile = inputIndexPath.resolve(columnsCsvFilename)

        if (Files.exists(columnsFile)) {
            return withContext(Dispatchers.IO) {
                Files.newBufferedReader(columnsFile).use { reader ->
                    val parser = CSVFormat.DEFAULT
                        .withFirstRecordAsHeader()
                        .parse(reader)

                    parser.map { it[1] }
                }
            }
        }

        val columnNames = ReportInput.open(inputPath)?.use { stream ->
            stream.header()
        } ?: throw IllegalArgumentException("Not found: $inputPath")

        val csvBody = columnNames
            .withIndex()
            .joinToString("\n") {
                CSVFormat.DEFAULT.format(it.index, it.value)
            }

        val csvFile = "Number,Name\n$csvBody"

        withContext(Dispatchers.IO) {
            Files.writeString(columnsFile, csvFile)
        }

        return columnNames
    }
}