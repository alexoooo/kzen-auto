package tech.kzen.auto.server.objects.report.pivot.row.digest

import com.sangupta.bloomfilter.AbstractBloomFilter
import com.sangupta.bloomfilter.BloomFilter
import com.sangupta.bloomfilter.core.BitArray
import com.sangupta.bloomfilter.core.MMapFileBackedBitArray
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


    //-----------------------------------------------------------------------------------------------------------------
    private fun getOrInit(): BloomFilter<Void> {
        if (filter != null) {
            return filter!!
        }

        Files.createDirectories(dir)
        val file = dir.resolve(fileName).toFile()

        filter = object : AbstractBloomFilter<Void>(numberOfElements, falsePositiveProbability) {
            override fun createBitArray(numBits: Int): BitArray {
                return MMapFileBackedBitArray(file, numBits)
            }
        }

        return filter!!
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        filter?.close()
    }
}