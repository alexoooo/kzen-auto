package tech.kzen.auto.server.objects.plugin

import tech.kzen.auto.plugin.definition.ProcessorDefinition
import tech.kzen.auto.server.service.plugin.ProcessorDefinitionMetadata
import tech.kzen.auto.server.service.plugin.ProcessorDefinitionRepository
import tech.kzen.lib.common.service.store.LocalGraphStore


class PluginProcessorDefinitionRepository(
    private val graphStore: LocalGraphStore
):
    ProcessorDefinitionRepository
{
    //-----------------------------------------------------------------------------------------------------------------




    //-----------------------------------------------------------------------------------------------------------------
    override fun contains(name: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun define(name: String): ProcessorDefinition<*> {
        TODO("Not yet implemented")
    }

    override fun listMetadata(): List<ProcessorDefinitionMetadata> {
        TODO("Not yet implemented")
    }
}