package tech.kzen.auto.common.objects.document.report.listing

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class HeaderLabel(
    val text: String,
    val occurrence: Int
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("ConstPropertyName", "RedundantSuppression")
    companion object {
        private const val encodingDelimiter = "|"

//        fun ofCollection(collection: List<String>): HeaderLabel {
//            val text = collection[0]
//            val occurrence = collection[1].toInt()
//            return HeaderLabel(text, occurrence)
//        }

        fun ofString(asString: String): HeaderLabel {
            val delimiterIndex = asString.indexOf(encodingDelimiter)
            val occurrence = asString.substring(0, delimiterIndex).toInt()
            val text = asString.substring(delimiterIndex + 1)
            return HeaderLabel(text, occurrence)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        require(occurrence >= 0)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        return "$occurrence$encodingDelimiter$text"
    }


    fun render(): String {
        return when {
            occurrence == 0 -> text
            else -> "$text (${occurrence + 1})"
        }
    }

//    fun asCollection(): List<String> {
//        return listOf(text, occurrence.toString())
//    }

    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(text)
        sink.addInt(occurrence)
    }
}