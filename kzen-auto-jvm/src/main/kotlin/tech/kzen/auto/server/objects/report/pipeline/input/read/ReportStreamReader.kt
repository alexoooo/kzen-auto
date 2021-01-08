package tech.kzen.auto.server.objects.report.pipeline.input.read

import com.google.common.io.MoreFiles
import org.apache.commons.io.input.BOMInputStream
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordItemParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.GZIPInputStream


// TODO: consider support for https://github.com/linkedin/migz
// TODO: consider using https://stackoverflow.com/questions/3335969/reading-a-gzip-file-from-a-filechannel-java-nio
// see: https://stackoverflow.com/questions/32550227/how-to-improve-gzip-performance
class ReportStreamReader(
    inputPath: Path,
    extraColumns: List<String> = listOf()
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val gzipBufferSize = 128 * 1024


        fun readHeaderLine(inputPath: Path): List<String> {
            return ReportStreamReader(inputPath).use {
                it.header().headerNames
            }
        }


        private fun outerExtension(inputPath: Path): String {
            return MoreFiles.getFileExtension(inputPath)
        }


        private fun innerExtension(inputPath: Path, outerExtension: String): String {
            return when (outerExtension) {
                "gz" -> {
                    val withoutExtension = MoreFiles.getNameWithoutExtension(inputPath)
                    MoreFiles.getFileExtension(Paths.get(withoutExtension))
                }

                else ->
                    outerExtension
            }
        }


        private fun openReader(inputPath: Path, outerExtension: String): Reader {
            val rawInput = Files.newInputStream(inputPath)

            val input =
                if (outerExtension == "gz") {
                    GZIPInputStream(rawInput, gzipBufferSize)
                }
                else {
                    rawInput
                }

            val bomInputStream = BOMInputStream(input)
            val inputStreamReader = InputStreamReader(bomInputStream)
            return BufferedReader(inputStreamReader)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val recordLineReader: RecordLineReader
    private val recordHeader: RecordHeader


    //-----------------------------------------------------------------------------------------------------------------
    init {
        val outerExtension = outerExtension(inputPath)
        val innerExtension = innerExtension(inputPath, outerExtension)

        val reader = openReader(inputPath, outerExtension)
        val recordLineParser = RecordItemParser.forExtension(innerExtension)

        recordLineReader = RecordLineReader(
            reader, recordLineParser)

        val headerLine = RecordItemBuffer()
        recordLineReader.read(headerLine)

        headerLine.addAll(extraColumns)
        recordHeader = RecordHeader.ofLine(headerLine)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun header(): RecordHeader {
        return recordHeader
    }


    fun read(recordLineBuffer: RecordItemBuffer): Boolean {
        return recordLineReader.read(recordLineBuffer)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        recordLineReader.close()
    }
}