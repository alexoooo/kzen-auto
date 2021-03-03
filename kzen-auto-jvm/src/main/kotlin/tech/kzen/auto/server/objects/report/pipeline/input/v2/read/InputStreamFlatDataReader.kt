package tech.kzen.auto.server.objects.report.pipeline.input.v2.read

import tech.kzen.auto.server.objects.report.pipeline.input.v2.model.ReadResult
import java.io.ByteArrayInputStream
import java.io.InputStream


class InputStreamFlatDataReader(
    private val inputStream: InputStream
):
    FlatDataReader
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofLiteral(bytes: ByteArray): FlatDataReader {
            return InputStreamFlatDataReader(
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