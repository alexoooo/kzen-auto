package tech.kzen.auto.server.service.plugin

import tech.kzen.auto.plugin.definition.ProcessorDefinitionInfo
import tech.kzen.lib.platform.ClassName


data class ProcessorDefinitionMetadata(
    val processorDefinitionInfo: ProcessorDefinitionInfo,
    val payloadType: ClassName
)