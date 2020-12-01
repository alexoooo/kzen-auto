package tech.kzen.auto.server.objects.report.pivot.row.value

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import tech.kzen.auto.server.objects.report.input.model.RecordTextFlyweight


class MapRowValueIndex: RowValueIndex {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val missingSentinel = -1L
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val index: Object2LongOpenHashMap<String> = Object2LongOpenHashMap()
    private val reverseIndex: MutableList<String> = mutableListOf()


    init {
        index.defaultReturnValue(missingSentinel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun getOrAddIndex(value: String): Long {
        val indexOrDefault = index.getLong(value)

        if (indexOrDefault != missingSentinel) {
            return indexOrDefault
        }

        val nextIndex = index.size.toLong()

        index[value] = nextIndex
        reverseIndex.add(value)

        return nextIndex
    }


    override fun getOrAddIndex(value: RecordTextFlyweight): Long {
        return getOrAddIndex(value.toString())
    }


    override fun getValue(valueIndex: Long): String {
        return reverseIndex[valueIndex.toInt()]
    }


    override fun close() {}
}