package tech.kzen.auto.server.service.plugin

import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.plugin.definition.ReportDefinition
import tech.kzen.auto.plugin.definition.ReportDefinitionInfo
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.server.objects.plugin.model.ClassLoaderHandle
import tech.kzen.lib.platform.ClassName


interface ReportDefinitionRepository {
    operator fun contains(coordinate: PluginCoordinate): Boolean
    fun metadata(coordinate: PluginCoordinate): ReportDefinitionMetadata?
    fun listMetadata(): List<ReportDefinitionMetadata>

    fun classLoaderHandle(coordinates: Set<PluginCoordinate>, parentClassLoader: ClassLoader): ClassLoaderHandle

    fun define(coordinate: PluginCoordinate, classLoaderHandle: ClassLoaderHandle): ReportDefinition<*>


    fun find(payloadType: ClassName, dataLocation: DataLocation): List<ReportDefinitionInfo> {
        val matchingPayload = listMetadata().filter { it.payloadType == payloadType }
        val matchingPayloadInfo = matchingPayload.map { it.reportDefinitionInfo }

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

        val doNotAvoid = matchingPayloadInfo.filter { it.priority != ReportDefinitionInfo.priorityAvoid }
        if (doNotAvoid.isNotEmpty()) {
            return doNotAvoid.sortedByDescending { it.priority }
        }

        return matchingPayloadInfo
    }
}