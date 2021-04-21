package tech.kzen.auto.server.objects.report

import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.common.objects.document.plugin.model.CommonTextEncodingSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputDataSpec
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.plugin.spec.DataEncodingSpec
import tech.kzen.auto.server.objects.plugin.PluginUtils.asPluginCoordinate
import tech.kzen.auto.server.service.ServerContext


object ReportUtils {
    // TODO: encoding override from user
    fun encoding(inputDataSpec: InputDataSpec): DataEncodingSpec {
        val processorDefinitionMetadata = ServerContext.definitionRepository.metadata(
            inputDataSpec.processorDefinitionCoordinate.asPluginCoordinate())
        return processorDefinitionMetadata.processorDefinitionInfo.dataEncoding
    }


    fun DataEncodingSpec.asCommon(): CommonDataEncodingSpec {
        return CommonDataEncodingSpec(
            textEncoding?.let { CommonTextEncodingSpec(it.getOrDefault().name()) })
    }


    fun PluginCoordinate.asCommonPluginCoordinate(): CommonPluginCoordinate {
        return CommonPluginCoordinate(name)
    }
}