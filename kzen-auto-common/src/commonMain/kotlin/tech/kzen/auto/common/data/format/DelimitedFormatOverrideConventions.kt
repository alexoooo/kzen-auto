package tech.kzen.auto.common.data.format


object DelimitedFormatOverrideConventions {
    const val delimiter = "delimiter"
    const val header = "header"
    const val encoding = "encoding"
    const val skipLeadingLines = "skipLeadingLines"
    const val commentPrefix = "commentPrefix"

    val supported = setOf(delimiter, header, encoding, skipLeadingLines, commentPrefix)
}
