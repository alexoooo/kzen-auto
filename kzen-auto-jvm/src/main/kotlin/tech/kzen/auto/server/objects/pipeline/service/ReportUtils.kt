package tech.kzen.auto.server.objects.pipeline.service

import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonTextEncodingSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputDataSpec
import tech.kzen.auto.plugin.spec.DataEncodingSpec
import tech.kzen.auto.server.service.plugin.ProcessorDefinitionMetadata


object ReportUtils {
    // TODO: encoding override from user
    fun encoding(
        inputDataSpec: InputDataSpec,
        processorDefinitionMetadata: ProcessorDefinitionMetadata?
    ): DataEncodingSpec? {
        return when {
            processorDefinitionMetadata != null ->
                encodingWithMetadata(inputDataSpec, processorDefinitionMetadata)

            else ->
                encodingWithoutMetadata(inputDataSpec)
        }
    }


    private fun encodingWithoutMetadata(
        inputDataSpec: InputDataSpec
    ): DataEncodingSpec? {
        return null
    }


    fun encodingWithMetadata(
        inputDataSpec: InputDataSpec,
        processorDefinitionMetadata: ProcessorDefinitionMetadata
    ): DataEncodingSpec {
        return processorDefinitionMetadata.processorDefinitionInfo.dataEncoding
    }


    fun DataEncodingSpec.asCommon(): CommonDataEncodingSpec {
        return CommonDataEncodingSpec(
            textEncoding?.let { CommonTextEncodingSpec(it.getOrDefault().name()) })
    }
}