package tech.kzen.auto.server.objects.report.input.parse

import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer


interface RecordLineParser {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val csvExtension = "csv"
        const val tsvExtension = "tsv"

        fun forExtension(extension: String): RecordLineParser {
            return when (extension) {
                "csv" ->
                    FastCsvLineParser()

                "tsv" ->
                    FastTsvLineParser()
//                    TsvLineParser()

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
        recordLineBuffer: RecordLineBuffer,
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
        recordLineBuffer: RecordLineBuffer
    ): Boolean
}