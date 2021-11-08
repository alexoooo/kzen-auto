package tech.kzen.auto.server.objects.report.exec.output.export

import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.server.objects.report.exec.ReportPipelineStage
import tech.kzen.auto.server.objects.report.exec.event.ReportOutputEvent
import java.nio.charset.Charset
import kotlin.math.ceil


class CharsetExportEncoder(
    charset: Charset
):
    ReportPipelineStage<ReportOutputEvent<*>>("export-encode")
{
    //-----------------------------------------------------------------------------------------------------------------
    private val encoder = charset.newEncoder()
    private val maxBytesPerChar = encoder.maxBytesPerChar().toDouble()


    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ReportOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (event.isSkipOrSentinel()) {
            return
        }

        encode(event.exportData)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun encode(output: DataRecordBuffer) {
        val charsLength = output.charsLength
        val maxOutputLength = ceil(maxBytesPerChar * charsLength).toInt()
        output.ensureByteCapacity(maxOutputLength)

        val inputBuffer = output.initializedCharBuffer(charsLength)
        val outputBuffer = output.initializedByteBuffer(maxOutputLength)

        encoder.reset()
        encoder.encode(inputBuffer, outputBuffer, true)
        encoder.flush(outputBuffer)

        output.bytesLength = outputBuffer.position()
    }
}