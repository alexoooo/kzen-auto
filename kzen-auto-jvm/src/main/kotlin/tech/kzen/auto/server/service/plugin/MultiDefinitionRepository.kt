package tech.kzen.auto.server.service.plugin

import tech.kzen.auto.plugin.definition.ProcessorDefinition
import tech.kzen.auto.plugin.model.PluginCoordinate


class MultiDefinitionRepository(
    private val repositories: List<ProcessorDefinitionRepository>
):
    ProcessorDefinitionRepository
{
    override fun contains(coordinate: PluginCoordinate): Boolean {
        return repositories.any { it.contains(coordinate) }
    }


    override fun metadata(coordinate: PluginCoordinate): ProcessorDefinitionMetadata {
        for (repository in repositories) {
            if (coordinate !in repository) {
                continue
            }

            return repository.metadata(coordinate)
        }
        throw IllegalArgumentException("Unknown: $coordinate")
    }


    override fun define(coordinate: PluginCoordinate): ProcessorDefinition<*> {
        val repository = repositories.find { it.contains(coordinate) }
            ?: throw IllegalArgumentException("Unknown: $coordinate")

        return repository.define(coordinate)
    }


    override fun listMetadata(): List<ProcessorDefinitionMetadata> {
        return repositories.flatMap { it.listMetadata() }
    }
}