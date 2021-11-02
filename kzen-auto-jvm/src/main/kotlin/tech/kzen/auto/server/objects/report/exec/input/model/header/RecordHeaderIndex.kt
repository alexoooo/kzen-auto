package tech.kzen.auto.server.objects.report.exec.input.model.header

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing


data class RecordHeaderIndex(
    val columnHeaders: HeaderListing
) {
    //-----------------------------------------------------------------------------------------------------------------
    private var cachedIndices = IntArray(0)
//    private var cachedRecordHeader = RecordHeader.empty
    private var cachedRecordHeader = HeaderListing.empty


    //-----------------------------------------------------------------------------------------------------------------
    fun indices(recordHeader: HeaderListing): IntArray {
        if (recordHeader == cachedRecordHeader) {
            cachedRecordHeader = recordHeader
            return cachedIndices
        }

        val indices = IntArray(columnHeaders.values.size)
        for (i in columnHeaders.values.indices) {
            val columnHeader = columnHeaders.values[i]
            indices[i] = recordHeader.values.indexOf(columnHeader)
        }

        cachedIndices = indices
        cachedRecordHeader = recordHeader

        return indices
    }
}