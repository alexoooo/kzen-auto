package tech.kzen.auto.server.objects.report.pipeline.input.parse

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.parse.csv.CsvRecordLexer
import tech.kzen.auto.server.objects.report.pipeline.input.parse.csv.CsvRecordParser
import tech.kzen.auto.server.objects.report.pipeline.input.parse.text.TextRecordLexer
import tech.kzen.auto.server.objects.report.pipeline.input.parse.text.TextRecordParser
import tech.kzen.auto.server.objects.report.pipeline.input.parse.tsv.TsvRecordLexer
import tech.kzen.auto.server.objects.report.pipeline.input.parse.tsv.TsvRecordParser


data class RecordFormat(
    val lexer: RecordLexer,
    val parser: RecordParser,
    val fixedHeader: RecordHeader?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val csvExtension = "csv"
        const val tsvExtension = "tsv"


        fun ofCsv(): RecordFormat {
            return RecordFormat(
                CsvRecordLexer(),
                CsvRecordParser(),
                null)
        }


        fun ofTsv(): RecordFormat {
            return RecordFormat(
                TsvRecordLexer(),
                TsvRecordParser(),
                null)
        }


        fun ofText(): RecordFormat {
            return RecordFormat(
                TextRecordLexer(),
                TextRecordParser(),
                TextRecordParser.header)
        }


        fun forExtension(extension: String): RecordFormat {
            return when (extension) {
                csvExtension -> ofCsv()
                tsvExtension -> ofTsv()
                else -> ofText()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withDefaultFixedHeader(defaultFixedHeader: RecordHeader?): RecordFormat {
        return when {
            fixedHeader != null -> this
            else -> copy(fixedHeader = defaultFixedHeader)
        }
    }
}