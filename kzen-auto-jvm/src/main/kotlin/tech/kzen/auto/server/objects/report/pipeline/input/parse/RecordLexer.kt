package tech.kzen.auto.server.objects.report.pipeline.input.parse

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordTokenBuffer


interface RecordLexer {
    //-----------------------------------------------------------------------------------------------------------------
    fun tokenize(
        recordTokenBuffer: RecordTokenBuffer,
        contentChars: CharArray,
        contentOffset: Int = 0,
        contentEnd: Int = contentChars.size
    )


    fun endOfStream(recordTokenBuffer: RecordTokenBuffer)
}