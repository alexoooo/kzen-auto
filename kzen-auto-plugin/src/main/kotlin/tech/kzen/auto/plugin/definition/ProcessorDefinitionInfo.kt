package tech.kzen.auto.plugin.definition

import tech.kzen.auto.plugin.spec.DataEncodingSpec


data class ProcessorDefinitionInfo(
    val name: String,
    val extensions: List<String>,
    val dataEncoding: DataEncodingSpec
)
