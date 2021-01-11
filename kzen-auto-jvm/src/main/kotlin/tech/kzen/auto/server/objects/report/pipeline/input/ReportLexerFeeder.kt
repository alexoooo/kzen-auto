package tech.kzen.auto.server.objects.report.pipeline.input

import tech.kzen.auto.server.objects.report.pipeline.input.model.BinaryDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordLexer
import java.nio.file.Path


class ReportLexerFeeder {
    //-----------------------------------------------------------------------------------------------------------------
    private var location: Path? = null
    private var lexer: RecordLexer? = null

//    private var count = 0


    //-----------------------------------------------------------------------------------------------------------------
    fun tokenize(data: BinaryDataBuffer) {
        if (location == null) {
            location = data.location!!

            lexer = RecordLexer.forExtension(data.innerExtension!!)
//            lexer = TsvRecordLexer()
        }

//        if (count >= 2152) {
//        if (String(data.contents).contains("2020-09-01\t2020-09-14\t10:00:55.144828341")) {
//            println("foo")
//        }

        lexer!!.tokenize(data.recordTokenBuffer, data.contents, 0, data.length)

        if (data.endOfStream) {
            lexer!!.endOfStream(data.recordTokenBuffer)
        }

//        count++
    }
}