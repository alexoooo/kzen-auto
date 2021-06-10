package tech.kzen.auto.common.objects.document.report.listing

import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.common.util.data.DataLocationGroup
import tech.kzen.auto.common.util.data.DataLocationInfo


data class InputDataInfo(
    val dataLocationInfo: DataLocationInfo,
    val processorDefinitionCoordinate: CommonPluginCoordinate,
    val dataEncodingSpec: CommonDataEncodingSpec?,
    val group: DataLocationGroup,
    val invalidProcessor: Boolean
):
    Comparable<InputDataInfo>
{
    companion object {
        private const val dataLocationInfoKey = "location"
        private const val processorDefinitionNameKey = "processor"
        private const val dataEncodingSpecKey = "encoding"
        private const val dataLocationGroupKey = "group"
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

            val groupText = collection[dataLocationGroupKey] as String
            val groupTextOrNull = groupText.ifEmpty { null }
            val group = DataLocationGroup(groupTextOrNull)

            return InputDataInfo(
                DataLocationInfo.ofCollection(collection[dataLocationInfoKey] as Map<String, String>),
                CommonPluginCoordinate.ofString(collection[processorDefinitionNameKey] as String),
                commonDataEncodingSpec,
                group,
                collection[invalidProcessorKey] as Boolean
            )
        }
    }


    fun asCollection(): Map<String, Any> {
        return mapOf(
            dataLocationInfoKey to dataLocationInfo.toCollection(),
            processorDefinitionNameKey to processorDefinitionCoordinate.asString(),
            dataEncodingSpecKey to (dataEncodingSpec?.asString() ?: ""),
            dataLocationGroupKey to (group.group ?: ""),
            invalidProcessorKey to invalidProcessor
        )
    }


    override fun compareTo(other: InputDataInfo): Int {
        val groupCmp = group.compareTo(other.group)
        if (groupCmp != 0) {
            return groupCmp
        }

        return dataLocationInfo.compareTo(other.dataLocationInfo)
    }
}