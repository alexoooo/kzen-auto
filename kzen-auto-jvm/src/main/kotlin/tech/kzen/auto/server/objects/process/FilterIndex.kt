package tech.kzen.auto.server.objects.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import tech.kzen.auto.util.AutoJvmUtils
import tech.kzen.auto.util.WorkUtils
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


object FilterIndex {
    //-----------------------------------------------------------------------------------------------------------------
    const val indexDirName = "index"


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun inputIndexPath(absoluteInputPath: Path): Path {
        val indexSubPath = AutoJvmUtils.sanitizeFilename(
            absoluteInputPath.fileName.toString())

        val pathInWork = Paths.get("${indexDirName}/$indexSubPath")
        val workPath = WorkUtils.resolve(pathInWork)

        if (! Files.isDirectory(workPath)) {
            withContext(Dispatchers.IO) {
                Files.createDirectories(workPath)
            }
        }

        return workPath
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCsv(csv: List<List<String>>): String {
        val out = StringWriter()
        CSVPrinter(out, CSVFormat.DEFAULT).use { csvPrinter ->
            for (row in csv) {
                csvPrinter.printRecord(row)
            }
        }
        return out.toString().trim()
    }


    fun fromCsv(csv: String): List<List<String>> {
        val reader = StringReader(csv)
        return CSVFormat.DEFAULT.parse(reader).use { parser ->
            parser.map { record -> record.toList() }
        }
    }
}