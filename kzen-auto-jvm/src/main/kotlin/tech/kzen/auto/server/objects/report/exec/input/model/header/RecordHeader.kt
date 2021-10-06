package tech.kzen.auto.server.objects.report.exec.input.model.header

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntMap
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.plugin.model.record.FlatFileRecord


// TODO: better name
data class RecordHeader(
    val headerNames: HeaderListing,
    val headerIndex: Object2IntMap<String>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val missingIndex = -1

        val empty = ofLine(FlatFileRecord())


        fun of(headerListing: HeaderListing): RecordHeader {
            return of(headerListing.values)
        }


        fun of(headerNames: List<String>): RecordHeader {
            return ofLine(FlatFileRecord.of(headerNames))
        }


        fun ofLine(recordLineBuffer: FlatFileRecord): RecordHeader {
            val headerNames = recordLineBuffer.toList()

            val headerIndex = Object2IntLinkedOpenHashMap<String>(headerNames.size)
            headerIndex.defaultReturnValue(missingIndex)

            for (i in headerNames.indices) {
                headerIndex[headerNames[i]] = i
            }

            return RecordHeader(HeaderListing(headerNames), headerIndex)
        }
    }


//    //-----------------------------------------------------------------------------------------------------------------
//    private val headerNames = mutableListOf<String>()
//    private val headerIndex = Object2IntLinkedOpenHashMap<String>()
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    init {
//        headerIndex.defaultReturnValue(missingIndex)
//    }


    //-----------------------------------------------------------------------------------------------------------------
    fun contains(headerName: String): Boolean {
        return headerIndex.contains(headerName)
    }


    fun isEmpty(): Boolean {
        return headerNames.values.isEmpty()
    }


//    //-----------------------------------------------------------------------------------------------------------------
//    fun set(recordHeaderBuffer: RecordHeader) {
//        if (headerNames == recordHeaderBuffer.headerNames) {
//            return
//        }
//
//        headerNames.clear()
//        headerNames.addAll(recordHeaderBuffer.headerNames)
//
//        headerIndex.clear()
//        headerIndex.putAll(recordHeaderBuffer.headerIndex)
//    }
//
//
//    fun set(recordLineBuffer: RecordLineBuffer) {
//        headerNames.clear()
//        headerNames.addAll(recordLineBuffer.toList())
//
//        headerIndex.clear()
//        for (i in headerNames.indices) {
//            headerIndex[headerNames[i]] = i
//        }
//    }
}