package tech.kzen.auto.server.objects.plugin

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.objects.document.plugin.PluginConventions
import tech.kzen.auto.plugin.definition.ProcessorDefinition
import tech.kzen.auto.server.service.plugin.ProcessorDefinitionMetadata
import tech.kzen.auto.server.service.plugin.ProcessorDefinitionRepository
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.ClassName


class PluginProcessorDefinitionRepository(
    private val graphStore: LocalGraphStore,
    private val graphDefiner: GraphDefiner,
    private val graphCreator: GraphCreator
):
    ProcessorDefinitionRepository
{
    //-----------------------------------------------------------------------------------------------------------------
    private val cache = mutableMapOf<ObjectLocation, ProcessorDefinitionMetadataCache>()

    private data class ProcessorDefinitionMetadataCache(
        val digest: Digest,
        val metadata: List<ProcessorDefinitionMetadata>)


    //-----------------------------------------------------------------------------------------------------------------
    private fun pluginProcessorDefinitionMetadata(): Map<ObjectLocation, List<ProcessorDefinitionMetadata>> {
        val graphStructure = runBlocking {
            graphStore.graphStructure()
        }

        val pluginObjectLocations = graphStructure
            .graphNotation
            .documents
            .values
            .filterValues { PluginConventions.isPlugin(it) }
            .keys
            .map { it.toMainObjectLocation() }
            .toSet()

        val objectNotations = graphStructure.graphNotation.coalesce
        if (cache.keys == pluginObjectLocations) {
            val allCached = pluginObjectLocations.all { cache[it]!!.digest == objectNotations[it]!!.digest() }
            if (allCached) {
                return cache.mapValues { it.value.metadata }
            }
            cache.clear()
        }

        val graphDefinitionAttempt = graphDefiner.tryDefine(graphStructure)
        val successfulGraphDefinition = graphDefinitionAttempt.transitiveSuccessful()

        val definedPluginObjectLocations = pluginObjectLocations
            .filter { it in successfulGraphDefinition.objectDefinitions }

        val pluginGraphDefinition = successfulGraphDefinition.filterTransitive(definedPluginObjectLocations)

        val pluginGraphInstance = graphCreator.createGraph(pluginGraphDefinition)

        val builder = mutableMapOf<ObjectLocation, List<ProcessorDefinitionMetadata>>()

        for (pluginObjectLocation in definedPluginObjectLocations) {
            val pluginDocument = pluginGraphInstance[pluginObjectLocation]!!.reference as PluginDocument

            val definersAndClassLoader = pluginDocument.loadDefiners()
                ?: continue

            val metadata = mutableListOf<ProcessorDefinitionMetadata>()

            try {
                for (processorDefiner in definersAndClassLoader.processorDefiners) {
                    val definition = processorDefiner.define()
                    val payloadType = ClassName(definition.processorDataDefinition.outputModelType.name)

                    metadata.add(ProcessorDefinitionMetadata(
                        processorDefiner.info(), payloadType))
                }
            }
            finally {
                definersAndClassLoader.classLoader.close()
            }

            builder[pluginObjectLocation] = metadata
            cache[pluginObjectLocation] = ProcessorDefinitionMetadataCache(
                objectNotations[pluginObjectLocation]!!.digest(), metadata)
        }

        return builder
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    override fun contains(name: String): Boolean {
        return pluginProcessorDefinitionMetadata()
            .any { e -> e.value.any { it.processorDefinitionInfo.name == name } }
    }


    @Synchronized
    override fun listMetadata(): List<ProcessorDefinitionMetadata> {
        return pluginProcessorDefinitionMetadata().values.flatten()
    }


    @Synchronized
    override fun define(name: String): ProcessorDefinition<*> {
        val pluginObjectLocation = pluginProcessorDefinitionMetadata()
            .filterValues { i -> i.any { it.processorDefinitionInfo.name == name } }
            .keys
            .single()

        val graphDefinitionAttempt = runBlocking {
            graphStore.graphDefinition()
        }

        val transitiveGraphDefinition = graphDefinitionAttempt.successful().filterTransitive(pluginObjectLocation)
        val graphInstance = graphCreator.createGraph(transitiveGraphDefinition)

        val pluginDocument = graphInstance[pluginObjectLocation]!!.reference as PluginDocument

        val definersAndClassLoader = pluginDocument.loadDefiners()
            ?: throw IllegalStateException("Unable to load: $name")

        val processorDefiner = definersAndClassLoader.processorDefiners.find { it.info().name == name }
            ?: throw IllegalStateException("Not found: $name")

        val processorDefinition = processorDefiner.define()

        @Suppress("UnnecessaryVariable")
        val withClassLoaderCloser = processorDefinition.copy(closer = {
            definersAndClassLoader.classLoader.close()
            processorDefinition.close()
        })

        return withClassLoaderCloser
    }
}