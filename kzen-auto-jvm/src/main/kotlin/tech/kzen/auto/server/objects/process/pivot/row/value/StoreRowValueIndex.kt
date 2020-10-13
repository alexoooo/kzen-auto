package tech.kzen.auto.server.objects.process.pivot.row.value

import tech.kzen.auto.server.objects.process.pivot.row.digest.DigestIndex
import tech.kzen.auto.server.objects.process.pivot.row.store.IndexedTextStore
import tech.kzen.lib.common.util.Digest


class StoreRowValueIndex(
    private val digestIndex: DigestIndex,
    private val indexedTextStore: IndexedTextStore
):
    RowValueIndex
{
    override fun getOrAddIndex(value: String): Long {
        val valueDigest = Digest.ofUtf8(value)
        val valueIndex = digestIndex.getOrAdd(valueDigest)

        if (valueIndex.wasAdded()) {
            indexedTextStore.add(value)
        }

        return valueIndex.ordinal()
    }


    override fun getValue(valueIndex: Long): String {
        return indexedTextStore.get(valueIndex)
    }


    override fun close() {
        digestIndex.close()
        indexedTextStore.close()
    }
}