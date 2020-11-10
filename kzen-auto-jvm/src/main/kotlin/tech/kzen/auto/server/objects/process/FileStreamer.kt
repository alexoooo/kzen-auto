package tech.kzen.auto.server.objects.process

import com.google.common.io.MoreFiles
import org.apache.commons.csv.CSVFormat
import org.apache.commons.io.input.BOMInputStream
import tech.kzen.auto.server.objects.process.stream.CsvRecordStream
import tech.kzen.auto.server.objects.process.stream.RecordStream
import tech.kzen.auto.server.objects.process.stream.TsvRecordStream
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

            reader = BufferedReader(InputStreamReader(BOMInputStream(input)))

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