package tech.kzen.auto.server.data.read.delimited


class DelimitedReadException(
    val category: String,
    val context: DelimitedReadContext,
    val recordIndex: Long,
    val fieldPath: String? = null,
    val span: LongRange? = null,
    detail: String
): IllegalArgumentException(buildString {
    append(category).append(" at ").append(context.source)
    context.unit?.let { append(", unit ").append(it) }
    context.part?.let { append(", part ").append(it) }
    append(", record ").append(recordIndex)
    fieldPath?.let { append(", field ").append(it) }
    span?.let { append(", span ").append(it.first).append("..").append(it.last) }
    append(": ").append(detail)
}) {
    companion object {
        const val syntax = "record-syntax"
        const val width = "record-width"
        const val header = "header-mapping"
        const val typedValue = "typed-value"
        const val budget = "read-budget"
        const val configuration = "reader-configuration"
    }
}
