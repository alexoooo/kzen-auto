package tech.kzen.auto.server.objects.report.pipeline.input.parse

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer


interface RecordParserOld {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val csvExtension = "csv"
        const val tsvExtension = "tsv"

        fun forExtension(extension: String): RecordParserOld {
            return when (extension) {
                "csv" ->
                    CsvRecordParser()

                "tsv" ->
                    TsvRecordParser()

                else ->
                    error("Unknown: $extension")
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return amount of content chars that were consumed to complete the record, or -1 if all were consumed
     *      without reaching end of record
     */
    fun parseNext(
        recordItemBuffer: RecordItemBuffer,
        contentChars: CharArray,
        contentOffset: Int = 0,
        contentEnd: Int = contentChars.size
    ): Int


    fun endOfStream(
        recordItemBuffer: RecordItemBuffer
    )
}