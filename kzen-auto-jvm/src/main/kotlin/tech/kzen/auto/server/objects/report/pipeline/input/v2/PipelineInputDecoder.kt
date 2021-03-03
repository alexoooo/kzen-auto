package tech.kzen.auto.server.objects.report.pipeline.input.v2

import tech.kzen.auto.plugin.model.DataBlockBuffer
import java.nio.ByteBuffer
import java.nio.charset.Charset


class PipelineInputDecoder(
    val charset: Charset
) {
    //-----------------------------------------------------------------------------------------------------------------
    private val decoder = charset.newDecoder()
    private val leftover = ByteBuffer.allocate(4)


    //-----------------------------------------------------------------------------------------------------------------
    fun decode(data: DataBlockBuffer) {
        decoder.reset()

        val input = data.byteBuffer.position(0).limit(data.bytesLength)
        val output = data.charBuffer.position(0).limit(data.chars.size)

        if (leftover.position() > 0) {
            while (true) {
                leftover.put(input.get())

                leftover.flip()
                val leftoverResult = decoder.decode(leftover, output, false)
                check(leftoverResult.isUnderflow)

                if (output.position() > 0) {
                    leftover.clear()
                    break
                }
                else {
                    leftover.position(leftover.limit())
                    leftover.limit(leftover.limit() + 1)
                }
            }
        }

        var cr = decoder.decode(input, output, true)
        if (cr.isUnderflow) {
            cr = decoder.flush(output)
        }

        if (cr.isMalformed) {
            check(! data.endOfStream)
            leftover.clear()
            for (i in cr.length() downTo 1) {
                leftover.put(data.bytes[data.bytesLength - i])
            }
        }
        else {
            check(cr.isUnderflow)
        }

        data.charsLength = output.position()
    }
}