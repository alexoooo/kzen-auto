package tech.kzen.auto.server.objects.report.pipeline.input.stages

import tech.kzen.auto.plugin.model.data.DataBlockBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.connect.FlatDataStream
import tech.kzen.auto.server.objects.report.pipeline.input.connect.InputStreamFlatDataStream
import tech.kzen.auto.server.objects.report.pipeline.progress.ReportProgressTracker


@Suppress("UnstableApiUsage")
class ProcessorInputReader(
    private val input: FlatDataStream,
    private val progress: ReportProgressTracker.Buffer? = null
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofLiteral(textBytes: ByteArray): ProcessorInputReader {
            return ProcessorInputReader(
                InputStreamFlatDataStream.ofLiteral(textBytes),
                null)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var endOfData = false


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return true if has next
     */
    fun poll(buffer: DataBlockBuffer): Boolean {
        check(! endOfData)

        val result = input.read(buffer.bytes)

        if (result.isEndOfData()) {
            buffer.setEndOfData()

            endOfData = true
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
    }
}