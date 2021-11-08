package tech.kzen.auto.server.service.plugin

import tech.kzen.auto.plugin.definition.ReportDefiner
import tech.kzen.auto.plugin.definition.ReportDefinition
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.server.objects.plugin.model.ClassLoaderHandle
import tech.kzen.lib.platform.ClassName


class HostReportDefinitionRepository(
    definers: List<ReportDefiner<*>>
):
    ReportDefinitionRepository
{
    //-----------------------------------------------------------------------------------------------------------------
    private val metadata = definers
        .map { ReportDefinitionMetadata(
            it.info(),
            ClassName(it.define().reportDataDefinition.outputModelType.name))
        }

    private val metadataByCoordinate = metadata
        .groupBy { it.reportDefinitionInfo.coordinate }
        .mapValues { it.value.single() }


    private val definersByCoordinate = definers
        .groupBy { it.info().coordinate }
        .mapValues { it.value.single() }


    //-----------------------------------------------------------------------------------------------------------------
    override fun contains(coordinate: PluginCoordinate): Boolean {
        return coordinate in definersByCoordinate
    }


    override fun metadata(coordinate: PluginCoordinate): ReportDefinitionMetadata? {
        return metadataByCoordinate[coordinate]
    }


    override fun listMetadata(): List<ReportDefinitionMetadata> {
        return metadata
    }


    override fun classLoaderHandle(
        coordinates: Set<PluginCoordinate>,
        parentClassLoader: ClassLoader
    ): ClassLoaderHandle {
        return ClassLoaderHandle.ofHost(parentClassLoader)
    }


    override fun define(
        coordinate: PluginCoordinate,
        classLoaderHandle: ClassLoaderHandle
    ): ReportDefinition<*> {
        val definer = definersByCoordinate[coordinate]
            ?: throw IllegalArgumentException("Missing: $coordinate")

        return definer.define()
    }
}