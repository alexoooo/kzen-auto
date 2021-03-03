package tech.kzen.auto.server.objects.report.pipeline.input.connect

import java.io.InputStream
import java.net.URI


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
    override fun key(): URI {
        return URI.create(key)
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