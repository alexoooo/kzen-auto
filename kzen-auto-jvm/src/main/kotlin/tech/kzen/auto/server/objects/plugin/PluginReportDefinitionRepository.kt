package tech.kzen.auto.server.objects.plugin

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.objects.document.plugin.PluginConventions
import tech.kzen.auto.plugin.definition.ReportDefinition
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.server.objects.plugin.model.ClassLoaderHandle
import tech.kzen.auto.server.service.plugin.ReportDefinitionMetadata
import tech.kzen.auto.server.service.plugin.ReportDefinitionRepository
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.platform.ClassName
import java.net.URLClassLoader


class PluginReportDefinitionRepository(
    private val graphStore: LocalGraphStore,
    private val graphDefiner: GraphDefiner,
    private val graphCreator: GraphCreator
):
    ReportDefinitionRepository
{
    //-----------------------------------------------------------------------------------------------------------------
    private val metadataByDefinerCache = mutableMapOf<ObjectLocation, DefinerMetadataCache>()

    private data class DefinerMetadataCache(
        val digest: Digest,
        val metadata: List<ReportDefinitionMetadata>)


    private var metadataByCoordinateCache = mutableMapOf<PluginCoordinate, DefinitionMetadataCache>()

    private data class DefinitionMetadataCache(
        val pluginObjectLocation: ObjectLocation,
        val metadata: ReportDefinitionMetadata)


    private var cachedStructureDigest: Digest = Digest.missing


    //-----------------------------------------------------------------------------------------------------------------
    private fun refreshCacheIfRequired() {
        val graphStructure = runBlocking {
            graphStore.graphStructure()
        }

        if (cachedStructureDigest == graphStructure.digest()) {
            return
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

        metadataByDefinerCache.keys.retainAll(pluginObjectLocations)
        for (pluginObjectLocation in pluginObjectLocations) {
            val cached = metadataByDefinerCache[pluginObjectLocation]
            if (cached != null && cached.digest != objectNotations[pluginObjectLocation]!!.digest()) {
                metadataByDefinerCache.remove(pluginObjectLocation)
            }
        }

        if (metadataByDefinerCache.keys == pluginObjectLocations) {
            return
        }

        val graphDefinitionAttempt = graphDefiner.tryDefine(graphStructure)
        val successfulGraphDefinition = graphDefinitionAttempt.transitiveSuccessful

        val definedPluginObjectLocations = pluginObjectLocations
            .filter { it in successfulGraphDefinition.objectDefinitions }

        val pluginGraphDefinition = successfulGraphDefinition.filterTransitive(definedPluginObjectLocations)

        val pluginGraphInstance = graphCreator.createGraph(pluginGraphDefinition)

        for (pluginObjectLocation in definedPluginObjectLocations) {
            if (pluginObjectLocation in metadataByDefinerCache) {
                continue
            }

            val pluginDocument = pluginGraphInstance[pluginObjectLocation]!!.reference as PluginDocument

            val classLoader = pluginDocument.jarClassLoader()
                ?: continue

            val metadata: List<ReportDefinitionMetadata> = classLoader.use {
                val processorDefiners = pluginDocument.loadDefiners(classLoader)

                if (processorDefiners == null) {
                    listOf()
                }
                else {
                    val builder = mutableListOf<ReportDefinitionMetadata>()

                    for (processorDefiner in processorDefiners) {
                        val definition = try {
                            processorDefiner.define()
                        }
                        catch (e: Throwable) {
                            continue
                        }

                        val payloadType = ClassName(definition.reportDataDefinition.outputModelType.name)

                        builder.add(ReportDefinitionMetadata(
                            processorDefiner.info(), payloadType))
                    }

                    builder
                }
            }

            metadataByDefinerCache[pluginObjectLocation] = DefinerMetadataCache(
                objectNotations[pluginObjectLocation]!!.digest(), metadata)
        }

        metadataByCoordinateCache.clear()
        for ((objectLocation, cache) in metadataByDefinerCache) {
            for (metadata in cache.metadata) {
                metadataByCoordinateCache[metadata.reportDefinitionInfo.coordinate] =
                    DefinitionMetadataCache(objectLocation, metadata)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    override fun contains(coordinate: PluginCoordinate): Boolean {
        refreshCacheIfRequired()
        return coordinate in metadataByCoordinateCache
    }


    @Synchronized
    override fun metadata(coordinate: PluginCoordinate): ReportDefinitionMetadata? {
        refreshCacheIfRequired()
        return metadataByCoordinateCache[coordinate]?.metadata
    }


    @Synchronized
    override fun listMetadata(): List<ReportDefinitionMetadata> {
        refreshCacheIfRequired()
        return metadataByCoordinateCache.values.map { it.metadata }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun classLoaderHandle(
        coordinates: Set<PluginCoordinate>,
        parentClassLoader: ClassLoader
    ): ClassLoaderHandle {
        refreshCacheIfRequired()

        val matchingMetadata = metadataByCoordinateCache
            .filterValues { it.metadata.reportDefinitionInfo.coordinate in coordinates }

        val pluginLocations = matchingMetadata
            .map { it.value.pluginObjectLocation }
            .toSet()

        check(pluginLocations.isNotEmpty()) {
            "Not found: $coordinates"
        }

        val graphDefinitionAttempt = runBlocking {
            graphStore.graphDefinition()
        }

        val transitiveGraphDefinition = graphDefinitionAttempt.successful().filterTransitive(pluginLocations)
        val graphInstance = graphCreator.createGraph(transitiveGraphDefinition)

        val jarUrls = pluginLocations
            .map { graphInstance[it]!!.reference as PluginDocument }
            .mapNotNull { it.jarRoot() }
            .mapNotNull { it.toURL() }

        check(jarUrls.isNotEmpty()) {
            "Missing: $pluginLocations"
        }

        val classLoader = URLClassLoader(
            "plugin",
            jarUrls.toTypedArray(),
            parentClassLoader)

        return ClassLoaderHandle.ofGuest(classLoader)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    override fun define(
        coordinate: PluginCoordinate,
        classLoaderHandle: ClassLoaderHandle
    ): ReportDefinition<*> {
        refreshCacheIfRequired()

        val pluginObjectLocation = metadataByCoordinateCache[coordinate]?.pluginObjectLocation
            ?: throw IllegalArgumentException("Name found: $coordinate")

        val graphDefinitionAttempt = runBlocking {
            graphStore.graphDefinition()
        }

        val transitiveGraphDefinition = graphDefinitionAttempt.successful().filterTransitive(pluginObjectLocation)
        val graphInstance = graphCreator.createGraph(transitiveGraphDefinition)

        val pluginDocument = graphInstance[pluginObjectLocation]!!.reference as PluginDocument

        val processorDefiners = pluginDocument.loadDefiners(classLoaderHandle.classLoader)
            ?: throw IllegalStateException("Unable to load: $coordinate")

        val processorDefiner = processorDefiners.find { it.info().coordinate == coordinate }
            ?: run {
                throw IllegalStateException("Not found: $coordinate")
            }

        @Suppress("UnnecessaryVariable")
        val processorDefinition = processorDefiner.define()

        return processorDefinition
    }
}