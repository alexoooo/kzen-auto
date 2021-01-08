package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.value

import net.openhft.hashing.LongTupleHashFunction
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordTextFlyweight
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.digest.DigestIndex
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.digest.DigestOrdinal
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.value.store.IndexedTextStore


class StoreRowValueIndex(
    private val digestIndex: DigestIndex,
    private val indexedTextStore: IndexedTextStore
):
    RowValueIndex
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val missingValueSentinel = "\u0000"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val hashBuffer = LongArray(2)


    //-----------------------------------------------------------------------------------------------------------------
    override fun size(): Long {
        return digestIndex.size()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun getOrAddIndex(value: String): DigestOrdinal {
        LongTupleHashFunction.murmur_3().hashChars(value, hashBuffer)
        val valueOrdinal = digestIndex.getOrAdd(hashBuffer[0], hashBuffer[1])

        if (valueOrdinal.wasAdded()) {
            indexedTextStore.add(value)
        }

        return valueOrdinal
    }


    override fun getOrAddIndex(value: RecordTextFlyweight): DigestOrdinal {
        LongTupleHashFunction.murmur_3().hashChars(value, hashBuffer)
        val valueOrdinal = digestIndex.getOrAdd(hashBuffer[0], hashBuffer[1])

        if (valueOrdinal.wasAdded()) {
            indexedTextStore.add(value)
        }

        return valueOrdinal
    }


    override fun getOrAddIndexMissing(): DigestOrdinal {
        return getOrAddIndex(missingValueSentinel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun getValue(valueIndex: Long): String? {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val value = indexedTextStore.get(valueIndex)

        return when (value) {
            missingValueSentinel ->
                null

            else ->
                value
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        digestIndex.close()
        indexedTextStore.close()
    }
}