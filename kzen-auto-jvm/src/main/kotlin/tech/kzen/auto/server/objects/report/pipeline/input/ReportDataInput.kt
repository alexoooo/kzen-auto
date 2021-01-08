package tech.kzen.auto.server.objects.report.pipeline.input

import com.google.common.io.MoreFiles
import org.apache.commons.io.input.BOMInputStream
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import tech.kzen.auto.server.objects.report.pipeline.input.model.BinaryDataBuffer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.GZIPInputStream


class ReportDataInput(
    reportRunSpec: ReportRunSpec,
    private val taskHandle: TaskHandle?
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
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val remainingInputs = reportRunSpec.inputs.toMutableList()
//    private val extraColumns = reportRunSpec.formula.formulas.keys.toList()

    private var currentInput: Path? = null
    private var currentInnerExtension: String? = null
    private var currentStream: Reader? = null


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return true if more data could be remaining
     */
    fun poll(buffer: BinaryDataBuffer): Boolean {
        if (taskHandle!!.cancelRequested()) {
            return false
        }

        val nextStream = nextStream()
        if (nextStream == null) {
            //buffer.clear()
            return false
        }

        buffer.location = currentInput!!
        buffer.innerExtension = currentInnerExtension!!

        val read = nextStream.read(buffer.contents)
        if (read == -1) {
            buffer.length = 0
            buffer.endOfStream = true
            currentStream!!.close()
            currentStream = null
        }
        else {
            buffer.length = read
            buffer.endOfStream = false
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
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        currentStream?.close()
    }
}