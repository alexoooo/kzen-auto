package tech.kzen.auto.server.objects.report.pipeline.input.v2

import tech.kzen.auto.plugin.model.DataBlockBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.FlatDataStream
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.InputStreamFlatDataStream
import tech.kzen.auto.server.objects.report.pipeline.progress.ReportProgressTracker


@Suppress("UnstableApiUsage")
class ProcessorInputReader(
    private val input: FlatDataStream,
//    private val closeAtEndOfStream: Boolean = true,
    private val progress: ReportProgressTracker.Buffer? = null
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofLiteral(textBytes: ByteArray): ProcessorInputReader {
            return ProcessorInputReader(
                InputStreamFlatDataStream.ofLiteral(textBytes),
//                true,
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

//            if (closeAtEndOfStream) {
//                close()
//            }

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
//        endOfData = true
    }
}