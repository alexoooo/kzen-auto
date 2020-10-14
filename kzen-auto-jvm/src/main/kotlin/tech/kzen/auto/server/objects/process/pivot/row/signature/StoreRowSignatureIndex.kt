package tech.kzen.auto.server.objects.process.pivot.row.signature

import tech.kzen.auto.server.objects.process.pivot.row.digest.DigestIndex
import tech.kzen.auto.server.objects.process.pivot.row.signature.store.IndexedSignatureStore


class StoreRowSignatureIndex(
    private val digestIndex: DigestIndex,
    private val indexedSignatureStore: IndexedSignatureStore
): RowSignatureIndex {
    override fun size(): Long {
        return digestIndex.size()
    }


    override fun getOrAddIndex(rowSignature: RowSignature): Long {
        val valueDigest = rowSignature.digest()
        val valueOrdinal = digestIndex.getOrAdd(valueDigest)

        if (valueOrdinal.wasAdded()) {
            indexedSignatureStore.add(rowSignature)
        }

        return valueOrdinal.ordinal()
    }


    override fun getSignature(signatureOrdinal: Long): RowSignature {
        return indexedSignatureStore.get(signatureOrdinal)
    }


    override fun close() {
        digestIndex.close()
        indexedSignatureStore.close()
    }
}