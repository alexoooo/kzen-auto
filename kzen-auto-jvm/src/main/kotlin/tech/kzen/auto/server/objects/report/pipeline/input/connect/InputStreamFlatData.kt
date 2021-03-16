package tech.kzen.auto.server.objects.report.pipeline.input.connect

import tech.kzen.auto.common.objects.document.report.listing.DataLocation
import java.io.InputStream


class InputStreamFlatData(
    private val inputStream: InputStream,
    private val outerExtension: String,
    private val innerExtension: String = outerExtension,
    private val size: Long = -1,
    private val key: String = "",
): FlatData {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofCsv(inputStream: InputStream): InputStreamFlatData {
            return InputStreamFlatData(
                inputStream, "csv")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun key(): DataLocation {
//        return DataLocation(URI.create(key))
        return DataLocation.unknown
    }


    override fun outerExtension(): String {
        return outerExtension
    }


    override fun innerExtension(): String {
        return innerExtension
    }


    override fun size(): Long {
        return size
    }


    override fun open(): InputStream {
        return inputStream
    }
}