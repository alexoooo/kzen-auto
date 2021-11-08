package tech.kzen.auto.server.objects.report.exec.input.stages

import tech.kzen.auto.plugin.model.data.DataBlockBuffer
import tech.kzen.auto.server.objects.report.exec.input.connect.FlatDataStream
import tech.kzen.auto.server.objects.report.exec.input.connect.InputStreamFlatDataStream
import tech.kzen.auto.server.objects.report.exec.trace.ReportInputTrace


@Suppress("UnstableApiUsage")
class ReportInputReader(
    private val input: FlatDataStream,
    private val progress: ReportInputTrace? = null
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofLiteral(textBytes: ByteArray): ReportInputReader {
            return ReportInputReader(
                InputStreamFlatDataStream.ofLiteral(textBytes),
                /*null*/)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var endOfData = false
    private var read = 0L


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return ReadResult
     */
    fun poll(buffer: DataBlockBuffer): Boolean {
        check(! endOfData)

        val result = input.read(buffer.bytes)

        if (result.isEndOfData()) {
//            println("ProcessorInputReader - end of data - $read")

            buffer.setEndOfData()
            endOfData = true
            return false
        }
        else {
            read += result.byteCount()
//            println("ProcessorInputReader - read - ${result.byteCount()}")

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