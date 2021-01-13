package tech.kzen.auto.server.objects.report.pipeline.input.parse

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer


interface RecordParser {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val csvExtension = "csv"
        const val tsvExtension = "tsv"

        fun forExtension(extension: String): RecordParser {
            return when (extension) {
                csvExtension ->
                    CsvLexerParser()

                tsvExtension ->
                    TsvLexerParser()

                else ->
                    error("Unknown: $extension")
            }
        }
    }


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