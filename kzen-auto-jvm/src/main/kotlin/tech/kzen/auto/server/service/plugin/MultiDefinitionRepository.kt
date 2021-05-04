package tech.kzen.auto.server.service.plugin

import tech.kzen.auto.plugin.definition.ProcessorDefinition
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.server.objects.plugin.model.ClassLoaderHandle


class MultiDefinitionRepository(
    private val repositories: List<ProcessorDefinitionRepository>
):
    ProcessorDefinitionRepository
{
    override fun contains(coordinate: PluginCoordinate): Boolean {
        return repositories.any { it.contains(coordinate) }
    }


    override fun metadata(coordinate: PluginCoordinate): ProcessorDefinitionMetadata? {
        for (repository in repositories) {
            if (coordinate !in repository) {
                continue
            }

            return repository.metadata(coordinate)
        }

        return null
    }


    override fun listMetadata(): List<ProcessorDefinitionMetadata> {
        return repositories.flatMap { it.listMetadata() }
    }


    override fun classLoaderHandle(
        coordinates: Set<PluginCoordinate>,
        parentClassLoader: ClassLoader
    ): ClassLoaderHandle {
        val chain = mutableListOf<ClassLoader>()
        chain.add(parentClassLoader)

        for (repository in repositories) {
            val matching = repository
                .listMetadata()
                .filter { it.processorDefinitionInfo.coordinate in coordinates }
                .map { it.processorDefinitionInfo.coordinate }
                .toSet()

            if (matching.isEmpty()) {
                continue
            }

            val parent = chain.last()
            val classLoaderHandle = repository.classLoaderHandle(matching, parent)
            chain.add(classLoaderHandle.classLoader)
        }

        val closerChain = chain.filterIsInstance<AutoCloseable>()
        val terminal = chain.last()

        return ClassLoaderHandle.ofChain(terminal, closerChain)
    }


    override fun define(
        coordinate: PluginCoordinate,
        classLoaderHandle: ClassLoaderHandle
    ): ProcessorDefinition<*> {
        val repository = repositories.find { it.contains(coordinate) }
            ?: throw IllegalArgumentException("Unknown: $coordinate")

        return repository.define(coordinate, classLoaderHandle)
    }
}