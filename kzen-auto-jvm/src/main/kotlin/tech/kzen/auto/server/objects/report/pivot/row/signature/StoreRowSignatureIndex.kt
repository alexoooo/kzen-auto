package tech.kzen.auto.server.objects.report.pivot.row.signature

import net.openhft.hashing.LongTupleHashFunction
import tech.kzen.auto.server.objects.report.pivot.row.digest.DigestIndex
import tech.kzen.auto.server.objects.report.pivot.row.signature.store.IndexedSignatureStore


class StoreRowSignatureIndex(
    private val digestIndex: DigestIndex,
    private val indexedSignatureStore: IndexedSignatureStore
): RowSignatureIndex {
    //-----------------------------------------------------------------------------------------------------------------
    private val hashBuffer = LongArray(2)


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

        return valueOrdinal.ordinal()
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