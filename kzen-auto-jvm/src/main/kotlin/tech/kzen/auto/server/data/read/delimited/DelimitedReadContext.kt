package tech.kzen.auto.server.data.read.delimited


data class DelimitedReadContext(
    val source: String,
    val unit: String? = null,
    val part: String? = null
)
