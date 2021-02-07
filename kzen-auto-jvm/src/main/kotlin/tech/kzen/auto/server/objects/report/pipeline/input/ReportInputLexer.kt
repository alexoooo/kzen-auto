package tech.kzen.auto.server.objects.report.pipeline.input

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordLexer


class ReportInputLexer {
    //-----------------------------------------------------------------------------------------------------------------
    private var location: String? = null
    private var lexer: RecordLexer? = null

//    private var count = 0


    //-----------------------------------------------------------------------------------------------------------------
    fun tokenize(data: RecordDataBuffer) {
        if (location == null) {
            location = data.inputKey!!

            lexer = RecordLexer.forExtension(data.innerExtension!!)
//            lexer = TsvRecordLexer()
        }

//        if (count >= 2152) {
//        if (String(data.contents).contains("2020-09-01\t2020-09-14\t10:00:55.144828341")) {
//            println("foo")
//        }

        lexer!!.tokenize(data.recordTokenBuffer, data.chars, 0, data.charsLength)

        if (data.endOfStream) {
            lexer!!.endOfStream(data.recordTokenBuffer)
        }

//        count++
    }
}