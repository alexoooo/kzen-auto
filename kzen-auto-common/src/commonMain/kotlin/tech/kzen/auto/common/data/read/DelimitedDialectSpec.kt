package tech.kzen.auto.common.data.read


data class DelimitedDialectSpec(
    val delimiter: String,
    val quote: String?,
    val escape: String,
    val emptyField: String,
    val trimming: String
)
