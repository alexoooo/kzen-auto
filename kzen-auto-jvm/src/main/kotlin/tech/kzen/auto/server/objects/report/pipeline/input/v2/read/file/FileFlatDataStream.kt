package tech.kzen.auto.server.objects.report.pipeline.input.v2.read.file

import com.google.common.io.CountingInputStream
import org.apache.commons.io.input.BOMInputStream
import tech.kzen.auto.server.objects.report.pipeline.input.v2.model.ReadResult
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.FlatDataStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.name


@OptIn(ExperimentalPathApi::class)
class FileFlatDataStream constructor(
    location: Path,
    gzip: Boolean = location.name.endsWith(".gz"),
    bomPrefix: Boolean = true
):
    FlatDataStream
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val gzipBufferSize = 128 * 1024

//        fun of(
//            dataLocation: DataLocation,
//            dataEncoding: DataEncodingSpec
//        ): FileFlatDataStream {
//            val location = dataLocation.uri.toPath()
//            val isText = dataEncoding.textEncoding != null
//            return FileFlatDataStream(
//                location,
//                bomPrefix = isText)
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var rawCountingInput: CountingInputStream
    private var input: InputStream
    private var previousRawCount: Long = 0


    //-----------------------------------------------------------------------------------------------------------------
    init {
        val raw = Files.newInputStream(location)

        rawCountingInput = CountingInputStream(raw)

        val extracted =
            if (gzip) {
                GZIPInputStream(rawCountingInput, gzipBufferSize)
            }
            else {
                rawCountingInput
            }

        val decoded =
            if (bomPrefix) {
                BOMInputStream(extracted)
            }
            else {
                extracted
            }

        input = decoded
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun read(byteArray: ByteArray): ReadResult {
        val byteCount = input.read(byteArray)

        if (byteCount == -1) {
            return ReadResult.endOfData
        }

        val rawCount = rawCountingInput.count
        val currentRawCount = rawCount - previousRawCount
        previousRawCount = rawCount

        return ReadResult.of(byteCount, currentRawCount.toInt())
    }


    override fun close() {
        input.close()
    }
}