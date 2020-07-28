package tech.kzen.auto.server.objects.filter

import com.google.common.io.MoreFiles
import org.apache.commons.csv.CSVFormat
import tech.kzen.auto.server.objects.filter.model.CsvRecordStream
import tech.kzen.auto.server.objects.filter.model.RecordStream
import tech.kzen.auto.server.objects.filter.model.TsvRecordStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.GZIPInputStream


object FileStreamer {
    fun open(inputPath: Path): RecordStream? {
//        val filename = inputPath.fileName
        val extension = MoreFiles.getFileExtension(inputPath)
        val withoutExtension = MoreFiles.getNameWithoutExtension(inputPath)
        
        var input: InputStream? = null
        var reader: BufferedReader? = null
        try {
            val adjustedExtension: String
            val rawInput = Files.newInputStream(inputPath)

            input =
                if (extension == "gz") {
                    adjustedExtension = MoreFiles.getFileExtension(Paths.get(withoutExtension))
                    GZIPInputStream(rawInput)
                }
                else {
                    adjustedExtension = extension
                    rawInput
                }

            reader = BufferedReader(InputStreamReader(input!!))

            return when (adjustedExtension) {
                "csv" -> {
                    CsvRecordStream(CSVFormat
                        .DEFAULT
                        .withFirstRecordAsHeader()
                        .parse(reader))
                }

                "tsv" -> {
                    TsvRecordStream(reader)
                }

                else -> {
                    throw UnsupportedOperationException("Unknown file type: $inputPath")
                }
            }
        }
        catch (e: Exception) {
            reader?.close()
            input?.close()
            return null
        }
    }
}