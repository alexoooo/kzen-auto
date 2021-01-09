package tech.kzen.auto.server.objects.report.pipeline.input

import com.google.common.io.MoreFiles
import org.apache.commons.io.input.BOMInputStream
import tech.kzen.auto.server.objects.report.pipeline.input.model.BinaryDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.progress.ReportProgress
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
class ReportDataFeeder(
    inputs: List<Path>,
    private val progress: ReportProgress?
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val gzipBufferSize = 128 * 1024


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


        fun single(input: Path): ReportDataFeeder {
            return ReportDataFeeder(listOf(input), null)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val remainingInputs = inputs.toMutableList()

    private var currentInput: Path? = null
    private var currentInnerExtension: String? = null
    private var currentStream: Reader? = null


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return true if more data could be remaining
     */
    fun poll(buffer: BinaryDataBuffer): Boolean {
        val nextStream = nextStream()
            ?: return false

        buffer.location = currentInput!!
        buffer.innerExtension = currentInnerExtension!!

        val read = nextStream.read(buffer.contents)
        if (read == -1) {
            buffer.length = 0
            buffer.endOfStream = true
            closeCurrent()
        }
        else {
            buffer.length = read
            buffer.endOfStream = false

            // TODO: handle compression
            progress?.next(currentInput!!, read.toLong(), read.toLong())
        }

        return true
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun nextStream(): Reader? {
        val existingStream = currentStream
        if (existingStream != null) {
            return existingStream
        }

        if (remainingInputs.isEmpty()) {
            return null
        }

        val nextInput = remainingInputs.removeFirst()
        openCurrent(nextInput)
        return currentStream!!
    }


    private fun openCurrent(nextInput: Path) {
        val outerExtension = outerExtension(nextInput)
        val innerExtension = innerExtension(nextInput, outerExtension)

        val rawInput = Files.newInputStream(nextInput)

        val input =
            if (outerExtension == "gz") {
                GZIPInputStream(rawInput, gzipBufferSize)
            }
            else {
                rawInput
            }

        val bomInputStream = BOMInputStream(input)
        val inputStreamReader = InputStreamReader(bomInputStream, Charsets.UTF_8)

        currentInput = nextInput
        currentInnerExtension = innerExtension
        currentStream = BufferedReader(inputStreamReader)

        progress?.start(nextInput, Files.size(nextInput))
    }


    private fun closeCurrent() {
        currentStream!!.close()
        currentStream = null

        progress?.end(currentInput!!)

        currentInput = null
        currentInnerExtension = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        currentStream?.close()
    }
}