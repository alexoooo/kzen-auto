package tech.kzen.auto.server.objects.report.pipeline.input

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordDataBuffer
import java.nio.ByteBuffer


// https://stackoverflow.com/questions/29559842/what-is-charsetdecoder-decodebytebuffer-charbuffer-endofinput
// https://stackoverflow.com/questions/14792654/decoding-multibyte-utf8-symbols-with-charset-decoder-in-byte-by-byte-manner
// https://stackoverflow.com/questions/45437358/streamdecoder-vs-inputstreamreader-when-reading-malformed-files
// https://stackoverflow.com/questions/499010/java-how-to-determine-the-correct-charset-encoding-of-a-stream
class ReportInputDecoder {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // TODO: expose to user
        private val charset = Charsets.UTF_8
//        private val charset = Charsets.ISO_8859_1
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val decoder = charset.newDecoder()
    private val leftover = ByteBuffer.allocate(4)


    //-----------------------------------------------------------------------------------------------------------------
    fun decode(data: RecordDataBuffer) {
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