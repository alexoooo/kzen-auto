package tech.kzen.auto.server.objects.process.pivot.row.value.store

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap


class BufferedIndexedTextStore(
    private val fileIndexedTextStore: FileIndexedTextStore,
    private val size: Int = 128
):
    IndexedTextStore
{
    //-----------------------------------------------------------------------------------------------------------------
    private val pending = mutableListOf<String>()
    private val pendingIndex = Long2ObjectOpenHashMap<String>()

    private val cachedIndex = Long2ObjectLinkedOpenHashMap<String>()

    private var nextOrdinal = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override fun add(text: String) {
        pending.add(text)
        pendingIndex[nextOrdinal] = text
        nextOrdinal++

        if (pending.size >= size) {
            fileIndexedTextStore.addAll(pending)
            pending.clear()
            pendingIndex.clear()
        }
    }


    override fun get(textOrdinal: Long): String {
        val pendingValue = pendingIndex.get(textOrdinal)
        if (pendingValue != null) {
            return pendingValue
        }

        val cachedValue = cachedIndex.getAndMoveToLast(textOrdinal)
        if (cachedValue != null) {
            return cachedValue
        }

        val readValue = fileIndexedTextStore.get(textOrdinal)
        cachedIndex[textOrdinal] = readValue

        if (cachedIndex.size >= size) {
            cachedIndex.removeFirst()
        }

        return readValue
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        fileIndexedTextStore.close()
    }
}