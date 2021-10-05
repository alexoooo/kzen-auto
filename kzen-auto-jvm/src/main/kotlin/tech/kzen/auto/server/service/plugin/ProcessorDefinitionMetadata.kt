package tech.kzen.auto.server.service.plugin

import tech.kzen.auto.common.objects.document.plugin.model.ProcessorDefinerDetail
import tech.kzen.auto.plugin.definition.ProcessorDefinitionInfo
import tech.kzen.auto.server.objects.plugin.PluginUtils.asCommon
import tech.kzen.auto.server.objects.pipeline.service.ReportUtils.asCommon
import tech.kzen.lib.platform.ClassName


data class ProcessorDefinitionMetadata(
    val processorDefinitionInfo: ProcessorDefinitionInfo,
    val payloadType: ClassName
) {
    fun toProcessorDefinerDetail(): ProcessorDefinerDetail {
        return ProcessorDefinerDetail(
            processorDefinitionInfo.coordinate.asCommon(),
            processorDefinitionInfo.extensions,
            processorDefinitionInfo.dataEncoding.asCommon(),
            processorDefinitionInfo.priority,
            payloadType
        )
    }
}