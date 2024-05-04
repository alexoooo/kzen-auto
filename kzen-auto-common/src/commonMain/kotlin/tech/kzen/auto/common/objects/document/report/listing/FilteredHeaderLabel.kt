package tech.kzen.auto.common.objects.document.report.listing
//
//import tech.kzen.lib.common.util.digest.Digest
//import tech.kzen.lib.common.util.digest.Digestible
//
//
//data class FilteredHeaderLabel(
//    val values: HeaderLabelMap<Boolean>
////    val headerLabel: HeaderLabel,
////    val included: Boolean
//): Digestible {
//    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        @Suppress("ConstPropertyName")
//        private const val encodingDelimiter = "|"
//
//        fun ofString(asString: String): FilteredHeaderLabel {
//            val delimiterIndex = asString.indexOf(encodingDelimiter)
//            val included = asString.substring(0, delimiterIndex).toBoolean()
//            val headerLabelAsString = asString.substring(delimiterIndex + 1)
//            val headerLabel = HeaderLabel.ofString(headerLabelAsString)
//            return FilteredHeaderLabel(headerLabel, included)
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    fun asString(): String {
//        return "$included$encodingDelimiter${headerLabel.asString()}"
//    }
//
//    override fun digest(sink: Digest.Sink) {
//        sink.addBoolean(included)
//        sink.addDigestible(headerLabel)
//    }
//}