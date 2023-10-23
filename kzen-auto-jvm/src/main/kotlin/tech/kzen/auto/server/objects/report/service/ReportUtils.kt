package tech.kzen.auto.server.objects.report.service

import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonTextEncodingSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputDataSpec
import tech.kzen.auto.plugin.spec.DataEncodingSpec
import tech.kzen.auto.server.service.plugin.ReportDefinitionMetadata


object ReportUtils {
    // TODO: encoding override from user
    fun encoding(
        inputDataSpec: InputDataSpec,
        reportDefinitionMetadata: ReportDefinitionMetadata?
    ): DataEncodingSpec? {
        return when {
            reportDefinitionMetadata != null ->
                encodingWithMetadata(inputDataSpec, reportDefinitionMetadata)

            else ->
                encodingWithoutMetadata(inputDataSpec)
        }
    }


    private fun encodingWithoutMetadata(
        @Suppress("UNUSED_PARAMETER")
        inputDataSpec: InputDataSpec
    ): DataEncodingSpec? {
        return null
    }


    fun encodingWithMetadata(
        @Suppress("UNUSED_PARAMETER")
        inputDataSpec: InputDataSpec,
        reportDefinitionMetadata: ReportDefinitionMetadata
    ): DataEncodingSpec {
        return reportDefinitionMetadata.reportDefinitionInfo.dataEncoding
    }


    fun DataEncodingSpec.asCommon(): CommonDataEncodingSpec {
        return CommonDataEncodingSpec(
            textEncoding?.let { CommonTextEncodingSpec(it.getOrDefault().name()) })
    }
}