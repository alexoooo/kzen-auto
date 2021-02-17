package tech.kzen.auto.server.objects.report.pipeline.input.parse

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer


interface RecordParser {
    //-----------------------------------------------------------------------------------------------------------------
    fun parseFull(
        recordItemBuffer: RecordItemBuffer,
        contentChars: CharArray,
        recordOffset: Int,
        recordLength: Int,
        fieldCount: Int
    )


    fun parsePartial(
        recordItemBuffer: RecordItemBuffer,
        contentChars: CharArray,
        recordOffset: Int,
        recordLength: Int,
        fieldCount: Int,
        endPartial: Boolean
    )
}