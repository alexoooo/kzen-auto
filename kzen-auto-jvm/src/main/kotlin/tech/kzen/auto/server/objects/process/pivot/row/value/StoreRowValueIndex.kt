package tech.kzen.auto.server.objects.process.pivot.row.value

import net.openhft.hashing.LongTupleHashFunction
import tech.kzen.auto.server.objects.process.pivot.row.digest.DigestIndex
import tech.kzen.auto.server.objects.process.pivot.row.value.store.IndexedTextStore


class StoreRowValueIndex(
    private val digestIndex: DigestIndex,
    private val indexedTextStore: IndexedTextStore/*,
    private val cacheSize: Int = 1024*/
):
    RowValueIndex
{
    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        private const val missingOrdinal = -1L
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private val hashBuffer = LongArray(2)

//    private val cache = Object2LongLinkedOpenHashMap<String>()
//
//    init {
//        cache.defaultReturnValue(missingOrdinal)
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun getOrAddIndex(value: String): Long {
//        val cachedOrdinal = cache.getAndMoveToLast(value)
//        if (cachedOrdinal != missingOrdinal) {
//            return cachedOrdinal
//        }

//        val digestHigh = LongHashFunction.metro().hashChars(value)
//        val digestLow = LongHashFunction.wy_3().hashChars(value)
//        val valueIndex = digestIndex.getOrAdd(digestHigh, digestLow)
        LongTupleHashFunction.murmur_3().hashChars(value, hashBuffer)
        val valueOrdinal = digestIndex.getOrAdd(hashBuffer[0], hashBuffer[1])

        if (valueOrdinal.wasAdded()) {
            indexedTextStore.add(value)
        }

        val ordinal = valueOrdinal.ordinal()

//        cache[value] = cachedOrdinal
//        if (cache.size == cacheSize) {
//            cache.removeFirstLong()
//        }

        return ordinal
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun getValue(valueIndex: Long): String {
        return indexedTextStore.get(valueIndex)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        digestIndex.close()
        indexedTextStore.close()
    }
}