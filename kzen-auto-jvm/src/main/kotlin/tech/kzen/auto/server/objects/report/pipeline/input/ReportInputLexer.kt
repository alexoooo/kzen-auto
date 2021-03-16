package tech.kzen.auto.server.objects.report.pipeline.input

import tech.kzen.auto.common.objects.document.report.listing.DataLocation
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordFormat
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordLexer


class ReportInputLexer {
    //-----------------------------------------------------------------------------------------------------------------
    private var previousLocation: DataLocation? = null
    private var previousLexer: RecordLexer? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun tokenize(data: RecordDataBuffer) {
        val lexer = openLexerIfRequired(data)

        lexer.tokenize(
            data.recordTokenBuffer,
            data.chars,
            0,
            data.charsLength)

        if (data.endOfStream) {
            closeLexer(data)
        }
    }


    private fun openLexerIfRequired(data: RecordDataBuffer): RecordLexer {
        if (previousLocation == null) {
            previousLocation = data.inputKey!!
            previousLexer = RecordFormat.forExtension(data.innerExtension!!).lexer
        }
        return previousLexer!!
    }


    private fun closeLexer(data: RecordDataBuffer) {
        previousLexer!!.endOfStream(data.recordTokenBuffer)
        previousLexer = null
        previousLocation = null
    }
}