package tech.kzen.auto.server.service.plugin

import tech.kzen.auto.plugin.definition.ProcessorDefinition
import tech.kzen.auto.plugin.definition.ProcessorDefinitionInfo
import tech.kzen.lib.platform.ClassName


interface ProcessorDefinitionRepository {
    fun contains(name: String): Boolean
    fun define(name: String): ProcessorDefinition<*>
    fun listMetadata(): List<ProcessorDefinitionMetadata>


    fun find(payloadType: ClassName, fileExtension: String, binary: Boolean): List<ProcessorDefinitionInfo> {
        val matchingPayload = listMetadata().filter { it.payloadType == payloadType }
        val matchingPayloadInfo = matchingPayload.map { it.processorDefinitionInfo }

        if (matchingPayloadInfo.isEmpty()) {
            return listOf()
        }
        else if (matchingPayloadInfo.size == 1) {
            return matchingPayloadInfo
        }

        val matchingEncoding = matchingPayloadInfo.filter { it.dataEncoding.isBinary() == binary }
        if (matchingEncoding.isEmpty()) {
            return matchingPayloadInfo
        }
        else if (matchingEncoding.size == 1) {
            return matchingEncoding
        }

        val matchingExtensions = matchingEncoding.filter { fileExtension in it.extensions }
        if (matchingExtensions.isNotEmpty()) {
            return matchingExtensions.sortedByDescending { it.priority }
        }

        val doNotAvoid = matchingEncoding.filter { it.priority != ProcessorDefinitionInfo.priorityAvoid }
        if (doNotAvoid.isNotEmpty()) {
            return doNotAvoid.sortedByDescending { it.priority }
        }

        return matchingEncoding
    }
}