package tech.kzen.auto.server.objects.report.input.read

import com.google.common.io.MoreFiles
import org.apache.commons.io.input.BOMInputStream
import tech.kzen.auto.server.objects.report.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer
import tech.kzen.auto.server.objects.report.input.parse.RecordLineParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.GZIPInputStream


class ReportStreamReader(
    inputPath: Path,
    extraColumns: List<String> = listOf()
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
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
                    GZIPInputStream(rawInput)
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
        val recordLineParser = RecordLineParser.forExtension(innerExtension)

        recordLineReader = RecordLineReader(
            reader, recordLineParser)

        val headerLine = RecordLineBuffer()
        recordLineReader.read(headerLine)

        headerLine.addAll(extraColumns)
        recordHeader = RecordHeader.ofLine(headerLine)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun header(): RecordHeader {
        return recordHeader
    }


    fun read(recordLineBuffer: RecordLineBuffer): Boolean {
        return recordLineReader.read(recordLineBuffer)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        recordLineReader.close()
    }
}