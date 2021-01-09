package tech.kzen.auto.server.objects.report.pipeline.input.parse

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer


interface RecordItemParser {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val csvExtension = "csv"
        const val tsvExtension = "tsv"

        fun forExtension(extension: String): RecordItemParser {
            return when (extension) {
                "csv" ->
                    FastCsvRecordParser()

                "tsv" ->
//                    FastTsvRecordParser()
                    FastTsvRecordParser2()

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


    //-----------------------------------------------------------------------------------------------------------------
//    /**
//     * @return true if reached end of record
//     */
//    fun parse(
//        recordLineBuffer: RecordLineBuffer,
//        nextChar: Char
//    ): Boolean


    /**
     * @return true if reached end of record
     */
    fun endOfStream(
        recordItemBuffer: RecordItemBuffer
    )
}