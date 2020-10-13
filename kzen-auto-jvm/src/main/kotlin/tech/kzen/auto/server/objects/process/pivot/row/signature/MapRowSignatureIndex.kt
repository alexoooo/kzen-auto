package tech.kzen.auto.server.objects.process.pivot.row.signature

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap


class MapRowSignatureIndex: RowSignatureIndex {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val missingSentinel = -1L
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val index: Object2LongOpenHashMap<RowSignature> = Object2LongOpenHashMap()
    private val reverseIndices: MutableList<RowSignature> = mutableListOf()


    init {
        index.defaultReturnValue(missingSentinel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun size(): Long {
        return index.size.toLong()
    }


    override fun getOrAddIndex(rowSignature: RowSignature): Long {
        val indexOrDefault = index.getLong(rowSignature)

        if (indexOrDefault != missingSentinel) {
            return indexOrDefault
        }

        val nextIndex = index.size.toLong()

        index[rowSignature] = nextIndex
        reverseIndices.add(rowSignature)

        return nextIndex
    }


    override fun getSignature(signatureOrdinal: Long): RowSignature {
        return reverseIndices[signatureOrdinal.toInt()]
    }


    override fun close() {}
}