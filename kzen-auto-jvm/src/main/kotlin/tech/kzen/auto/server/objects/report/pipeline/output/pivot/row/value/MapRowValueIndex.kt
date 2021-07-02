package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.value

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import tech.kzen.auto.plugin.model.record.FlatFileRecordField
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.digest.DigestOrdinal


class MapRowValueIndex: RowValueIndex {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val missingOrdinalSentinel = -1L
        private const val missingValueSentinel = "\u0000"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val index: Object2LongOpenHashMap<String> = Object2LongOpenHashMap()
    private val reverseIndex: MutableList<String> = mutableListOf()


    //-----------------------------------------------------------------------------------------------------------------
    init {
        index.defaultReturnValue(missingOrdinalSentinel)
    }

    //-----------------------------------------------------------------------------------------------------------------
    override fun size(): Long {
        return index.size.toLong()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun getOrAddIndex(value: String): DigestOrdinal {
        val indexOrDefault = index.getLong(value)

        if (indexOrDefault != missingOrdinalSentinel) {
            return DigestOrdinal.ofExisting(indexOrDefault)
        }

        val nextIndex = index.size.toLong()

        index[value] = nextIndex
        reverseIndex.add(value)

        return DigestOrdinal.ofAdded(nextIndex)
    }


    override fun getOrAddIndex(value: FlatFileRecordField): DigestOrdinal {
        return getOrAddIndex(value.toString())
    }


    override fun getOrAddIndexMissing(): DigestOrdinal {
        return getOrAddIndex(missingValueSentinel)
    }


    override fun getValue(valueIndex: Long): String? {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val value = reverseIndex[valueIndex.toInt()]

        return when (value) {
            missingValueSentinel -> null
            else -> value
        }
    }


    override fun close() {}
}