package tech.kzen.auto.server.objects.report.pivot.row.digest

import com.sangupta.bloomfilter.AbstractBloomFilter
import com.sangupta.bloomfilter.core.BitArray
import com.sangupta.bloomfilter.core.FileBackedBitArray
import java.io.File
import java.nio.file.Path


class DigestBloomFilter(
    dir: Path,
    private val heapSize: Int = 1024 * 1024
) {
    init {
        val numberOfElements = 1000 * 1000
        val fpp = 0.01

        val filter = object : AbstractBloomFilter<String?>(numberOfElements, fpp) {
            /**
             * Used a [FileBackedBitArray] to allow for file persistence.
             *
             * @returns a [BitArray] that will take care of storage of bloom filter
             */
            override fun createBitArray(numBits: Int): BitArray {
                return FileBackedBitArray(File("/tmp/test.bloom.filter"), numBits)
            }
        }
    }

//    private val filter = AbstractBloomFilter<Long>(1000 * 1000, 0.03) {
//        override fun createBitArray(numBits: Int): BitArray {
//            return FileBackedBitArray(File("/tmp/test.bloom.filter"), numBits)
//        }
//    }


//    fun mightContain(digestHigh: Long, digestLow: Long): Boolean {
//
//    }
}