package tech.kzen.auto.server.objects.process.pivot.row.signature.store

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import tech.kzen.auto.server.objects.process.pivot.row.signature.RowSignature


class BufferedIndexedSignatureStore(
    private val fileIndexedSignatureStore: FileIndexedSignatureStore,
    private val size: Int = 128
): IndexedSignatureStore {
    //------------------------------------m-----------------------------------------------------------------------------
    private val pending = mutableListOf<RowSignature>()
    private val pendingIndex = Long2ObjectOpenHashMap<RowSignature>()

    private val cachedIndex = Long2ObjectLinkedOpenHashMap<RowSignature>()

    private var nextOrdinal = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override fun add(signature: RowSignature) {
        pending.add(signature)
        pendingIndex[nextOrdinal] = signature
        nextOrdinal++

        if (pending.size >= size) {
            flush()
        }
    }


    override fun get(signatureOrdinal: Long): RowSignature {
        val pendingValue = pendingIndex.get(signatureOrdinal)
        if (pendingValue != null) {
            return pendingValue
        }

        val cachedValue = cachedIndex.getAndMoveToLast(signatureOrdinal)
        if (cachedValue != null) {
            return cachedValue
        }

        val readValue = fileIndexedSignatureStore.get(signatureOrdinal)
        cachedIndex[signatureOrdinal] = readValue

        if (cachedIndex.size >= size) {
            cachedIndex.removeFirst()
        }

        return readValue
    }


    private fun flush() {
        if (pending.isEmpty()) {
            return
        }

        fileIndexedSignatureStore.addAll(pending)
        pending.clear()
        pendingIndex.clear()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        flush()
        fileIndexedSignatureStore.close()
    }
}