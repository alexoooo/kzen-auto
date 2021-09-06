package tech.kzen.auto.server.objects.pipeline.exec.output.pivot.store

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap


class BufferedOffsetStore(
    private val fileOffsetStore: FileOffsetStore,
    private val batchSize: Int = 1024,
    private val cacheSize: Int = 4 * 1024
):
    OffsetStore
{
    //-----------------------------------------------------------------------------------------------------------------
    private val addBatch = IntArrayList()
    private val addIndex = Long2ObjectOpenHashMap<OffsetStore.Span>()

    private val cacheIndex = Long2ObjectLinkedOpenHashMap<OffsetStore.Span>()

    private var endOffset: Long = fileOffsetStore.endOffset()


    //-----------------------------------------------------------------------------------------------------------------
    override fun size(): Long {
        return fileOffsetStore.size() + addBatch.size
    }


    override fun endOffset(): Long {
        return endOffset
    }


    override fun get(index: Long): OffsetStore.Span {
        val cachedSpan = cacheIndex.getAndMoveToLast(index)
        if (cachedSpan != null) {
            return cachedSpan
        }

        val addingSpan = addIndex.get(index)
        if (addingSpan != null) {
            addCached(index, addingSpan)
            return addingSpan
        }

        val readSpan = fileOffsetStore.get(index)
        addCached(index, readSpan)

        return readSpan
    }


    private fun addCached(index: Long, span: OffsetStore.Span) {
        cacheIndex[index] = span

        if (cacheIndex.size == cacheSize) {
            cacheIndex.removeFirst()
        }
    }


    override fun add(length: Int) {
        val nextOffset = endOffset
        val nextOrdinal = size()

        endOffset += length
        addBatch.add(length)

        addIndex[nextOrdinal] = OffsetStore.Span(nextOffset, length)

        if (addBatch.size == batchSize) {
            flush()
        }
    }


    override fun addAll(lengths: IntList) {
        val iterator = lengths.iterator()
        while (iterator.hasNext()) {
            val length = iterator.nextInt()
            add(length)
        }
    }


    private fun flush() {
        if (addBatch.isEmpty) {
            return
        }

        fileOffsetStore.addAll(addBatch)
        addBatch.clear()
        addIndex.clear()
    }


    override fun close() {
        flush()
        fileOffsetStore.close()
    }
}