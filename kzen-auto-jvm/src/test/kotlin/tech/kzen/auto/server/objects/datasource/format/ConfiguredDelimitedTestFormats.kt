package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.schema.RecordSchema


object ConfiguredDelimitedTestFormats {
    fun csv(
        schema: RecordSchema? = null,
        charset: String = "UTF-8",
        delimiter: String = ",",
        recordSeparator: String = "lf",
        contentCodings: List<String> = emptyList()
    ): ConfiguredDelimitedFormat = ConfiguredDelimitedFormat(
        "CSV",
        listOf("csv"),
        true,
        delimiter,
        "\"",
        "double-quote",
        recordSeparator,
        "none",
        "present",
        charset,
        "permit",
        "report",
        "report",
        "",
        schema,
        contentCodings = contentCodings)


    fun tsv(schema: RecordSchema? = null, charset: String = "UTF-8"): ConfiguredDelimitedFormat =
        ConfiguredDelimitedFormat(
            "TSV",
            listOf("tsv"),
            true,
            "\t",
            "\"",
            "double-quote",
            "lf",
            "none",
            "present",
            charset,
            "permit",
            "report",
            "report",
            "",
            schema)
}
