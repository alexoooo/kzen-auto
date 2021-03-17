package tech.kzen.auto.server.objects.report.pipeline.input

import com.google.common.io.CountingInputStream
import org.apache.commons.io.input.BOMInputStream
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.server.objects.report.pipeline.input.connect.FlatData
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.progress.ReportProgressTracker
import java.io.InputStream
import java.util.zip.GZIPInputStream


// TODO: consider support for https://github.com/linkedin/migz
// TODO: consider using https://stackoverflow.com/questions/3335969/reading-a-gzip-file-from-a-filechannel-java-nio
// see: https://stackoverflow.com/questions/32550227/how-to-improve-gzip-performance
@Suppress("UnstableApiUsage")
class ReportInputReader(
    inputs: List<FlatData>,
    private val progress: ReportProgressTracker?,
    private val closeAtEndOfStream: Boolean = true
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val gzipBufferSize = 128 * 1024

        fun singleWithoutClose(input: FlatData): ReportInputReader {
            return ReportInputReader(listOf(input), null, false)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val remainingInputs = inputs.toMutableList()

    private var currentInputKey: DataLocation? = null
    private var currentInnerExtension: String? = null
    private var currentRawInput: CountingInputStream? = null
    private var currentStream: InputStream? = null
    private var currentProgress: ReportProgressTracker.Buffer? = null
    private var previousReadBytes: Long = 0L


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return true if did not reach end
     */
    fun poll(buffer: RecordDataBuffer): Boolean {
        val nextStream = nextStream()
            ?: return false

        buffer.inputKey = currentInputKey!!
        buffer.innerExtension = currentInnerExtension!!

        val read = nextStream.read(buffer.bytes)
        if (read == -1) {
            buffer.bytesLength = 0
            buffer.endOfStream = true
            if (closeAtEndOfStream) {
                closeCurrent()
            }
            return remainingInputs.isNotEmpty()
        }

        buffer.bytesLength = read
        buffer.endOfStream = false

        val rawBytes = currentRawInput!!.count
        val nextReadBytes: Long = rawBytes - previousReadBytes
        previousReadBytes = rawBytes
        currentProgress?.nextRead(nextReadBytes, read.toLong())

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

        currentRawInput = CountingInputStream(nextInput.open())

        val input =
            if (outerExtension == "gz") {
                GZIPInputStream(currentRawInput, gzipBufferSize)
            }
            else {
                currentRawInput
            }

        val bomInputStream = BOMInputStream(input)

        currentInputKey = nextInput.key()
        currentInnerExtension = innerExtension
        currentStream = bomInputStream

        currentProgress = progress?.getInitial(currentInputKey!!, nextInput.size())
        currentProgress?.startReading()
    }


    private fun closeCurrent() {
        currentStream!!.close()
        currentStream = null

        currentRawInput = null
        previousReadBytes = 0

        currentInputKey = null
        currentInnerExtension = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        currentStream?.close()
    }
}