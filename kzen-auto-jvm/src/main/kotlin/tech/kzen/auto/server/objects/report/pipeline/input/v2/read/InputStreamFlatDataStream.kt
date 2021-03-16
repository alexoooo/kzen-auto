package tech.kzen.auto.server.objects.report.pipeline.input.v2.read

import tech.kzen.auto.server.objects.report.pipeline.input.v2.model.ReadResult
import java.io.ByteArrayInputStream
import java.io.InputStream


class InputStreamFlatDataStream(
    private val inputStream: InputStream
):
    FlatDataStream
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofLiteral(bytes: ByteArray): FlatDataStream {
            return InputStreamFlatDataStream(
                ByteArrayInputStream(bytes))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun read(byteArray: ByteArray): ReadResult {
        val read = inputStream.read(byteArray)
        return ReadResult.ofInputStream(read)
    }


    override fun close() {
        inputStream.close()
    }
}