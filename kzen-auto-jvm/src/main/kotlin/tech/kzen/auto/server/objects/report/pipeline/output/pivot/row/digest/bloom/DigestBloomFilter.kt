package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.digest.bloom

import com.sangupta.bloomfilter.AbstractBloomFilter
import com.sangupta.bloomfilter.BloomFilter
import com.sangupta.bloomfilter.core.BitArray
import java.nio.file.Files
import java.nio.file.Path


class DigestBloomFilter(
    private val dir: Path
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val numberOfElements = 1_000_000_000
        private const val falsePositiveProbability = 0.001
        private const val fileName = "bloom.bin"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var filter: BloomFilter<Void>? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun add(digestBytes: ByteArray): Boolean {
        return getOrInit().add(digestBytes)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun mightContain(digestBytes: ByteArray): Boolean {
        return getOrInit().contains(digestBytes)
    }


    fun falsePositiveProbability(size: Long): Double {
        if (size > Integer.MAX_VALUE) {
            return 1.0
        }

        return filter
            ?.getFalsePositiveProbability(size.toInt())
            ?: 0.0
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun getOrInit(): BloomFilter<Void> {
        if (filter != null) {
            return filter!!
        }

        Files.createDirectories(dir)
        val file = dir.resolve(fileName)

        filter = object : AbstractBloomFilter<Void>(numberOfElements, falsePositiveProbability) {
            override fun createBitArray(numBits: Int): BitArray {
//                return MMapFileBackedBitArray(file.toFile(), numBits)
                return MmapBitArray(file, numBits)
            }
        }

        return filter!!
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        filter?.close()
    }
}