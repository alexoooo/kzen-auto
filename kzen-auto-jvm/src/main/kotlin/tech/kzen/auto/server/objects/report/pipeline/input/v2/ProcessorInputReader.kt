package tech.kzen.auto.server.objects.report.pipeline.input.v2

import tech.kzen.auto.plugin.model.DataBlockBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.FlatDataReader
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.InputStreamFlatDataReader
import tech.kzen.auto.server.objects.report.pipeline.progress.ReportProgressTracker


@Suppress("UnstableApiUsage")
class ProcessorInputReader(
    private val input: FlatDataReader,
    private val closeAtEndOfStream: Boolean = true,
    private val progress: ReportProgressTracker.Buffer? = null
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofLiteral(textBytes: ByteArray): ProcessorInputReader {
            return ProcessorInputReader(
                InputStreamFlatDataReader.ofLiteral(textBytes),
                true,
                null)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var doneOrClosed = false


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return true if has next
     */
    fun poll(buffer: DataBlockBuffer): Boolean {
        check(! doneOrClosed)

        val result = input.read(buffer.bytes)

        if (result.isEndOfData()) {
            buffer.setEndOfStream()

            if (closeAtEndOfStream) {
                close()
            }

            doneOrClosed = true
            return false
        }
        else {
            buffer.readNext(result.byteCount())
        }

        progress?.nextRead(
            result.rawByteCount().toLong(),
            result.byteCount().toLong())

        return true
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        input.close()
        doneOrClosed = true
    }
}