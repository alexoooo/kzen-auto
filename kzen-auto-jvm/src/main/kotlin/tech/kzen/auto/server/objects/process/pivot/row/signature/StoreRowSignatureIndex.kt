package tech.kzen.auto.server.objects.process.pivot.row.signature

import net.openhft.hashing.LongTupleHashFunction
import tech.kzen.auto.server.objects.process.pivot.row.digest.DigestIndex
import tech.kzen.auto.server.objects.process.pivot.row.signature.store.IndexedSignatureStore


class StoreRowSignatureIndex(
    private val digestIndex: DigestIndex,
    private val indexedSignatureStore: IndexedSignatureStore/*,
    private val cacheSize: Int = 4 * 1024*/
): RowSignatureIndex {
    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        private const val missingOrdinal = -1L
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private val hashBuffer = LongArray(2)

//    private val cache = Object2LongLinkedOpenHashMap<RowSignature>()
//
//    init {
//        cache.defaultReturnValue(missingOrdinal)
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun size(): Long {
        return digestIndex.size()
    }


    override fun getOrAddIndex(rowSignature: RowSignature): Long {
        LongTupleHashFunction.murmur_3().hashLongs(rowSignature.valueIndexes, hashBuffer)
        val valueOrdinal = digestIndex.getOrAdd(hashBuffer[0], hashBuffer[1])

        if (valueOrdinal.wasAdded()) {
            indexedSignatureStore.add(rowSignature)
        }

        val ordinal = valueOrdinal.ordinal()

//        cache[rowSignature] = cachedOrdinal
//        if (cache.size == cacheSize) {
//            cache.removeFirstLong()
//        }

        return ordinal
    }


    override fun getSignature(signatureOrdinal: Long): RowSignature {
        return indexedSignatureStore.get(signatureOrdinal)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        digestIndex.close()
        indexedSignatureStore.close()
    }
}