package tech.kzen.auto.server.objects.report.pipeline.output.export

import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.server.objects.report.pipeline.ProcessorPipelineStage
import tech.kzen.auto.server.objects.report.pipeline.event.ProcessorOutputEvent
import java.nio.charset.Charset
import kotlin.math.ceil


class CharsetExportEncoder(
    val charset: Charset
):
    ProcessorPipelineStage<ProcessorOutputEvent<*>>("export-encode")
{
    //-----------------------------------------------------------------------------------------------------------------
    private val encoder = charset.newEncoder()
    private val maxBytesPerChar = encoder.maxBytesPerChar().toDouble()


    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ProcessorOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (event.skip) {
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