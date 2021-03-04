package tech.kzen.auto.server.objects.report.pipeline.input.parse

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer


interface RecordParser {
    //-----------------------------------------------------------------------------------------------------------------
    fun parseFull(
            recordRowBuffer: RecordRowBuffer,
            contentChars: CharArray,
            recordOffset: Int,
            recordLength: Int,
            fieldCount: Int
    )


    fun parsePartial(
            recordRowBuffer: RecordRowBuffer,
            contentChars: CharArray,
            recordOffset: Int,
            recordLength: Int,
            fieldCount: Int,
            endPartial: Boolean
    )
}