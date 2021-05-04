package tech.kzen.auto.server.service.plugin

import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.plugin.definition.ProcessorDefinition
import tech.kzen.auto.plugin.definition.ProcessorDefinitionInfo
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.server.objects.plugin.model.ClassLoaderHandle
import tech.kzen.lib.platform.ClassName


interface ProcessorDefinitionRepository {
    operator fun contains(coordinate: PluginCoordinate): Boolean
    fun metadata(coordinate: PluginCoordinate): ProcessorDefinitionMetadata?
    fun listMetadata(): List<ProcessorDefinitionMetadata>

    fun classLoaderHandle(coordinates: Set<PluginCoordinate>, parentClassLoader: ClassLoader): ClassLoaderHandle

    fun define(coordinate: PluginCoordinate, classLoaderHandle: ClassLoaderHandle): ProcessorDefinition<*>


    fun find(payloadType: ClassName, dataLocation: DataLocation): List<ProcessorDefinitionInfo> {
        val matchingPayload = listMetadata().filter { it.payloadType == payloadType }
        val matchingPayloadInfo = matchingPayload.map { it.processorDefinitionInfo }

        if (matchingPayloadInfo.isEmpty()) {
            return listOf()
        }
        else if (matchingPayloadInfo.size == 1) {
            return matchingPayloadInfo
        }

        val fileExtension = dataLocation.innerExtension()

        val matchingExtensions = matchingPayloadInfo
            .filter { fileExtension in it.extensions }

        if (matchingExtensions.isNotEmpty()) {
            return matchingExtensions.sortedByDescending { it.priority }
        }

        val doNotAvoid = matchingPayloadInfo.filter { it.priority != ProcessorDefinitionInfo.priorityAvoid }
        if (doNotAvoid.isNotEmpty()) {
            return doNotAvoid.sortedByDescending { it.priority }
        }

        return matchingPayloadInfo
    }
}