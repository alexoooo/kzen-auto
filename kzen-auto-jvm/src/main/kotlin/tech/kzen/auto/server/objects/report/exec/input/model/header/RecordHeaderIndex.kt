package tech.kzen.auto.server.objects.report.exec.input.model.header

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing


data class RecordHeaderIndex(
    val columnHeaders: HeaderListing
) {
    //-----------------------------------------------------------------------------------------------------------------
    private var cachedIndices = IntArray(0)
    private var cachedHeaderListing = HeaderListing.empty


    //-----------------------------------------------------------------------------------------------------------------
    fun indices(headerListing: HeaderListing): IntArray {
        if (headerListing == cachedHeaderListing) {
            cachedHeaderListing = headerListing
            return cachedIndices
        }

        val indices = IntArray(columnHeaders.values.size)
        for (i in columnHeaders.values.indices) {
            val columnHeader = columnHeaders.values[i]
            indices[i] = headerListing.values.indexOf(columnHeader)
        }

        cachedIndices = indices
        cachedHeaderListing = headerListing

        return indices
    }
}