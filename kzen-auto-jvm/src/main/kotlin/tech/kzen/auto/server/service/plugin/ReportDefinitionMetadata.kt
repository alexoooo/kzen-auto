package tech.kzen.auto.server.service.plugin

import tech.kzen.auto.common.objects.document.plugin.model.ReportDefinerDetail
import tech.kzen.auto.plugin.definition.ReportDefinitionInfo
import tech.kzen.auto.server.objects.plugin.PluginUtils.asCommon
import tech.kzen.auto.server.objects.report.service.ReportUtils.asCommon
import tech.kzen.lib.platform.ClassName


data class ReportDefinitionMetadata(
    val reportDefinitionInfo: ReportDefinitionInfo,
    val payloadType: ClassName
) {
    fun toProcessorDefinerDetail(): ReportDefinerDetail {
        return ReportDefinerDetail(
            reportDefinitionInfo.coordinate.asCommon(),
            reportDefinitionInfo.extensions,
            reportDefinitionInfo.dataEncoding.asCommon(),
            reportDefinitionInfo.priority,
            payloadType
        )
    }
}