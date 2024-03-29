package tech.kzen.auto.server.objects.report.exec.input.connect

import tech.kzen.auto.server.objects.report.exec.input.model.ReadResult
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