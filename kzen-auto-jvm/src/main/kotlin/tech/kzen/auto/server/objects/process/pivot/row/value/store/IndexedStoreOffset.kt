package tech.kzen.auto.server.objects.process.pivot.row.value.store

import it.unimi.dsi.fastutil.ints.IntList


interface IndexedStoreOffset: AutoCloseable {
    data class Span(
        val offset: Long,
        val length: Int
    ) {
//        fun endOffset(): Long {
//            return offset + length
//        }
    }


    fun size(): Long
    fun endOffset(): Long

    fun get(index: Long): Span

    fun add(length: Int)
    fun addAll(lengths: IntList)
}