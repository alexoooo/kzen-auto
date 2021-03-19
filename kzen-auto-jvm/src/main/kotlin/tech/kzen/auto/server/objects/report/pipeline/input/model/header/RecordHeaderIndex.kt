package tech.kzen.auto.server.objects.report.pipeline.input.model.header

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing


data class RecordHeaderIndex(
    val columnHeaders: HeaderListing
) {
    //-----------------------------------------------------------------------------------------------------------------
    private var cachedIndices = IntArray(0)
    private var cachedRecordHeader = RecordHeader.empty


    //-----------------------------------------------------------------------------------------------------------------
    fun indices(recordHeader: RecordHeader): IntArray {
        if (recordHeader == cachedRecordHeader) {
            cachedRecordHeader = recordHeader
            return cachedIndices
        }

//        val indices = IntArray(recordHeader.headerNames.size)
        val indices = IntArray(columnHeaders.values.size)
        for (i in columnHeaders.values.indices) {
            val columnHeader = columnHeaders.values[i]
//            indices[i] = columnHeaders.indexOf(recordHeader.headerNames[i])
            indices[i] = recordHeader.headerNames.values.indexOf(columnHeader)
        }

        cachedIndices = indices
        cachedRecordHeader = recordHeader

        return indices
    }
}