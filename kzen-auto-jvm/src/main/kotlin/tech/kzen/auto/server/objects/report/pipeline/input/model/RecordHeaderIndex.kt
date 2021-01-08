package tech.kzen.auto.server.objects.report.pipeline.input.model


data class RecordHeaderIndex(
    val columnHeaders: List<String>
) {
    //-----------------------------------------------------------------------------------------------------------------
    private var cachedIndices = IntArray(0)
    private var cachedRecordHeader = RecordHeader.empty


    //-----------------------------------------------------------------------------------------------------------------
    fun indices(recordHeader: RecordHeader): IntArray {
        if (recordHeader === cachedRecordHeader) {
            return cachedIndices
        }

//        val indices = IntArray(recordHeader.headerNames.size)
        val indices = IntArray(columnHeaders.size)
        for (i in columnHeaders.indices) {
            val columnHeader = columnHeaders[i]
//            indices[i] = columnHeaders.indexOf(recordHeader.headerNames[i])
            indices[i] = recordHeader.headerNames.indexOf(columnHeader)
        }

        cachedIndices = indices
        cachedRecordHeader = recordHeader

        return indices
    }
}