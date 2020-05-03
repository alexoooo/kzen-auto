package tech.kzen.auto.common.util


object DataFormatUtils {
    fun csvEncode(value: String): String {
        val needsEncoding =
            value.contains(",") ||
            value.contains("\"")

        if (! needsEncoding) {
            return value
        }

        val escaped = value.replace("\"", "\"\"")

        return "\"$escaped\""
    }


    fun csvDecode(encodedValue: String): String {
        val needsDecoding = encodedValue.contains("\"")

        if (! needsDecoding) {
            return encodedValue
        }

        val withoutQuotes = encodedValue.substring(1, encodedValue.length - 1)

        @Suppress("UnnecessaryVariable")
        val decoded = withoutQuotes.replace("\"\"", "\"")

        return decoded
    }


    fun csvNextLength(encodedDocument: String, startIndex: Int): Int {
        val first = encodedDocument[startIndex]

        if (first != '"') {
            val nextIndex = encodedDocument.indexOf(',', startIndex)
            if (nextIndex == -1) {
                return encodedDocument.length - startIndex
            }
            return nextIndex - startIndex
        }

        var afterPreviousQuote = startIndex + 1
        var nextQuoteIndex = encodedDocument.indexOf("\"", afterPreviousQuote)
        var nextDoubleQuoteIndex = encodedDocument.indexOf("\"\"", afterPreviousQuote)

        while (nextQuoteIndex == nextDoubleQuoteIndex) {
            afterPreviousQuote = nextQuoteIndex + 2
            nextQuoteIndex = encodedDocument.indexOf("\"", afterPreviousQuote)
            nextDoubleQuoteIndex = encodedDocument.indexOf("\"\"", afterPreviousQuote)
        }

        return nextQuoteIndex - startIndex
    }
}