package tech.kzen.auto.server.objects.report.pipeline.input.parse

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordTokenBuffer


interface RecordLexer {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val csvExtension = "csv"
        const val tsvExtension = "tsv"

        fun forExtension(extension: String): RecordLexer {
            return when (extension) {
                csvExtension ->
                    CsvRecordLexer()

                tsvExtension ->
                    TsvRecordLexer()

                else ->
                    error("Unknown: $extension")
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun tokenize(
        recordTokenBuffer: RecordTokenBuffer,
        contentChars: CharArray,
        contentOffset: Int = 0,
        contentEnd: Int = contentChars.size
    )


    fun endOfStream(recordTokenBuffer: RecordTokenBuffer)
}