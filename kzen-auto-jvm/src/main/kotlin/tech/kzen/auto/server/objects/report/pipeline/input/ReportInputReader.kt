package tech.kzen.auto.server.objects.report.pipeline.input

import org.apache.commons.io.input.BOMInputStream
import tech.kzen.auto.server.objects.report.pipeline.input.connect.FileFlatData
import tech.kzen.auto.server.objects.report.pipeline.input.connect.FlatData
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.progress.ReportProgress
import java.io.InputStream
import java.nio.file.Path
import java.util.zip.GZIPInputStream



// TODO: consider support for https://github.com/linkedin/migz
// TODO: consider using https://stackoverflow.com/questions/3335969/reading-a-gzip-file-from-a-filechannel-java-nio
// see: https://stackoverflow.com/questions/32550227/how-to-improve-gzip-performance
class ReportInputReader(
    inputs: List<FlatData>,
    private val progress: ReportProgress?
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val gzipBufferSize = 128 * 1024


//        private fun outerExtension(inputPath: Path): String {
//            return MoreFiles.getFileExtension(inputPath)
//        }
//
//
//        private fun innerExtension(inputPath: Path, outerExtension: String): String {
//            return when (outerExtension) {
//                "gz" -> {
//                    val withoutExtension = MoreFiles.getNameWithoutExtension(inputPath)
//                    MoreFiles.getFileExtension(Paths.get(withoutExtension))
//                }
//
//                else ->
//                    outerExtension
//            }
//        }


        fun file(input: Path): ReportInputReader {
            return single(FileFlatData(input))
        }

        fun single(input: FlatData): ReportInputReader {
            return ReportInputReader(listOf(input), null)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val remainingInputs = inputs.toMutableList()

    private var currentInput: String? = null
    private var currentInnerExtension: String? = null
//    private var currentStream: Reader? = null
    private var currentStream: InputStream? = null


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return true if more data could be remaining
     */
    fun poll(buffer: RecordDataBuffer): Boolean {
        val nextStream = nextStream()
            ?: return false

        buffer.location = currentInput!!
        buffer.innerExtension = currentInnerExtension!!

        val read = nextStream.read(buffer.bytes)
        if (read == -1) {
            buffer.bytesLength = 0
            buffer.endOfStream = true
            closeCurrent()
            return remainingInputs.isNotEmpty()
        }

        buffer.bytesLength = read
        buffer.endOfStream = false

        // TODO: handle compression
        progress?.next(currentInput!!, read.toLong(), read.toLong())
        return true
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun nextStream(): InputStream? {
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


    private fun openCurrent(nextInput: FlatData) {
        val outerExtension = nextInput.outerExtension()
        val innerExtension = nextInput.innerExtension()

        val rawInput = nextInput.open()

        val input =
            if (outerExtension == "gz") {
                GZIPInputStream(rawInput, gzipBufferSize)
            }
            else {
                rawInput
            }

        val bomInputStream = BOMInputStream(input)
//        val inputStreamReader = InputStreamReader(bomInputStream, Charsets.UTF_8)

        currentInput = nextInput.key()
        currentInnerExtension = innerExtension
//        currentStream = BufferedReader(inputStreamReader)
        currentStream = bomInputStream

        progress?.start(currentInput!!, nextInput.size())
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