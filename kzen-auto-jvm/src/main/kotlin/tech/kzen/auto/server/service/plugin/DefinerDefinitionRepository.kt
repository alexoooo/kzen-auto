package tech.kzen.auto.server.service.plugin

import tech.kzen.auto.plugin.definition.ProcessorDefiner
import tech.kzen.auto.plugin.definition.ProcessorDefinition
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.lib.platform.ClassName


class DefinerDefinitionRepository(
    definers: List<ProcessorDefiner<*>>
):
    ProcessorDefinitionRepository
{
    //-----------------------------------------------------------------------------------------------------------------
    private val metadata = definers
        .map { ProcessorDefinitionMetadata(
            it.info(),
            ClassName(it.define().processorDataDefinition.outputModelType.name))
        }

    private val metadataByCoordinate = metadata
        .groupBy { it.processorDefinitionInfo.coordinate }
        .mapValues { it.value.single() }


    private val definersByCoordinate = definers
        .groupBy { it.info().coordinate }
        .mapValues { it.value.single() }


    //-----------------------------------------------------------------------------------------------------------------
    override fun contains(coordinate: PluginCoordinate): Boolean {
        return coordinate in definersByCoordinate
    }


    override fun metadata(coordinate: PluginCoordinate): ProcessorDefinitionMetadata {
        return metadataByCoordinate[coordinate]
            ?: throw IllegalArgumentException("Not found: $coordinate")
    }


    override fun define(coordinate: PluginCoordinate): ProcessorDefinition<*> {
        val definer = definersByCoordinate[coordinate]
            ?: throw IllegalArgumentException("Missing: $coordinate")

        return definer.define()
    }


    override fun listMetadata(): List<ProcessorDefinitionMetadata> {
        return metadata
    }
}