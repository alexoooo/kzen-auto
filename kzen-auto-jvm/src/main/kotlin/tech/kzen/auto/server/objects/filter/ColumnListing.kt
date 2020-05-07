package tech.kzen.auto.server.objects.filter

import com.google.common.io.MoreFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path


object ColumnListing {
    //-----------------------------------------------------------------------------------------------------------------
    private const val columnsCsvFilename = "columns.csv"


    //-----------------------------------------------------------------------------------------------------------------
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