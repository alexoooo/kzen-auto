package tech.kzen.auto.server.service.plugin

import tech.kzen.auto.plugin.definition.ProcessorDefinition


class MultiDefinitionRepository(
    private val repositories: List<ProcessorDefinitionRepository>
):
    ProcessorDefinitionRepository
{
    override fun contains(name: String): Boolean {
        return repositories.any { it.contains(name) }
    }


    override fun define(name: String): ProcessorDefinition<*> {
        val repository = repositories.find { it.contains(name) }
            ?: throw IllegalArgumentException("Unknown: $name")

        return repository.define(name)
    }


    override fun listMetadata(): List<ProcessorDefinitionMetadata> {
        return repositories.flatMap { it.listMetadata() }
    }
}