package tech.kzen.auto.server.objects.report.pivot.row.value

import net.openhft.hashing.LongTupleHashFunction
import tech.kzen.auto.server.objects.report.input.model.RecordTextFlyweight
import tech.kzen.auto.server.objects.report.pivot.row.digest.DigestIndex
import tech.kzen.auto.server.objects.report.pivot.row.value.store.IndexedTextStore


class StoreRowValueIndex(
    private val digestIndex: DigestIndex,
    private val indexedTextStore: IndexedTextStore
):
    RowValueIndex
{
    //-----------------------------------------------------------------------------------------------------------------
    private val hashBuffer = LongArray(2)


    //-----------------------------------------------------------------------------------------------------------------
    override fun getOrAddIndex(value: String): Long {
        LongTupleHashFunction.murmur_3().hashChars(value, hashBuffer)
        val valueOrdinal = digestIndex.getOrAdd(hashBuffer[0], hashBuffer[1])

        if (valueOrdinal.wasAdded()) {
            indexedTextStore.add(value)
        }

        return valueOrdinal.ordinal()
    }


    override fun getOrAddIndex(value: RecordTextFlyweight): Long {
        LongTupleHashFunction.murmur_3().hashChars(value, hashBuffer)
        val valueOrdinal = digestIndex.getOrAdd(hashBuffer[0], hashBuffer[1])

        if (valueOrdinal.wasAdded()) {
            indexedTextStore.add(value)
        }

        return valueOrdinal.ordinal()
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