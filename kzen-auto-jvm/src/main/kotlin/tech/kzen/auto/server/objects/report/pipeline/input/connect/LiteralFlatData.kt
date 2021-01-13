package tech.kzen.auto.server.objects.report.pipeline.input.connect

import tech.kzen.lib.common.util.Digest
import java.io.ByteArrayInputStream
import java.io.InputStream


data class LiteralFlatData(
    val bytes: ByteArray,
    val innerExtension: String,
    val outerExtension: String = innerExtension,
    val location: String = Digest.ofBytes(bytes).asString() + ".$innerExtension"
): FlatData {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofCsv(utf8: String): LiteralFlatData {
            return LiteralFlatData(utf8.encodeToByteArray(), "csv")
        }

        fun ofTsv(utf8: String): LiteralFlatData {
            return LiteralFlatData(utf8.encodeToByteArray(), "tsv")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun key(): String {
        return location
    }


    override fun outerExtension(): String {
        return outerExtension
    }


    override fun innerExtension(): String {
        return innerExtension
    }


    override fun size(): Long {
        return bytes.size.toLong()
    }


    override fun open(): InputStream {
        return ByteArrayInputStream(bytes)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LiteralFlatData

        if (!bytes.contentEquals(other.bytes)) return false
        if (innerExtension != other.innerExtension) return false
        if (outerExtension != other.outerExtension) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + innerExtension.hashCode()
        result = 31 * result + outerExtension.hashCode()
        return result
    }
}