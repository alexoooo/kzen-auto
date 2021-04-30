package tech.kzen.auto.common.objects.document.report.listing

import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.common.util.data.DataLocationInfo


data class InputDataInfo(
    val dataLocationInfo: DataLocationInfo,
    val processorDefinitionCoordinate: CommonPluginCoordinate,
    val dataEncodingSpec: CommonDataEncodingSpec?,
    val invalidProcessor: Boolean
) {
    companion object {
        private const val dataLocationInfoKey = "location"
        private const val processorDefinitionNameKey = "processor"
        private const val dataEncodingSpecKey = "encoding"
        private const val invalidProcessorKey = "invalid"


        @Suppress("UNCHECKED_CAST")
        fun ofCollection(collection: Map<String, Any>): InputDataInfo {
            val dataEncodingSpecValue = collection[dataEncodingSpecKey] as String
            val commonDataEncodingSpec =
                if (dataEncodingSpecValue.isEmpty()) {
                    null
                }
                else {
                    CommonDataEncodingSpec.ofString(dataEncodingSpecValue)
                }


            return InputDataInfo(
                DataLocationInfo.ofCollection(collection[dataLocationInfoKey] as Map<String, String>),
                CommonPluginCoordinate.ofString(collection[processorDefinitionNameKey] as String),
                commonDataEncodingSpec,
                collection[invalidProcessorKey] as Boolean
            )
        }
    }


    fun asCollection(): Map<String, Any> {
        return mapOf(
            dataLocationInfoKey to dataLocationInfo.toCollection(),
            processorDefinitionNameKey to processorDefinitionCoordinate.asString(),
            dataEncodingSpecKey to (dataEncodingSpec?.asString() ?: ""),
            invalidProcessorKey to invalidProcessor
        )
    }
}