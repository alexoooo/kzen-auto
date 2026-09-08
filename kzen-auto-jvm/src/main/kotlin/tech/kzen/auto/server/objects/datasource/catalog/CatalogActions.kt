package tech.kzen.auto.server.objects.datasource.catalog

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tech.kzen.auto.common.data.catalog.CatalogConventions
import tech.kzen.auto.common.data.catalog.CatalogSource
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.common.objects.document.job.JobChannelSynthesis
import tech.kzen.auto.server.service.exec.GraphInstanceCache
import tech.kzen.auto.server.service.exec.ObjectInstanceAttempt
import tech.kzen.auto.server.service.exec.ServerGraphDefinition
import tech.kzen.lib.common.exec.*
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.store.LocalGraphStore

@Reflect
class CatalogActions(
    @Service private val graphStore: LocalGraphStore,
    @Service private val cache: GraphInstanceCache,
    @Service private val metadata: NotationMetadataReader
): DetachedAction {
    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val source = request.getSingle(CatalogConventions.source)
            ?: return ExecutionFailure("Select a catalog source")
        val location = ObjectLocation.parse(source)
        val definition = JobChannelSynthesis(metadata).synthesizeOpenOutputsForObject(
            graphStore.graphDefinition().transitiveSuccessful, location).graphDefinition
        val instance = cache.tryObjectInstance(ServerGraphDefinition.of(definition), location)
        val catalog = (instance as? ObjectInstanceAttempt.Created)?.objectInstance?.reference as? CatalogSource
            ?: return ExecutionFailure("The selected source does not provide a catalog")
        return try {
            val action = request.getSingle(CatalogConventions.action)
            when (action) {
                "list", "refresh" -> Unit
                "prepare" -> catalog.prepare(selection(request))
                "cancel" -> catalog.cancel(selection(request))
                else -> return ExecutionFailure("Unknown catalog action: $action")
            }
            ExecutionSuccess.ofValue(ExecutionValue.of(Json.encodeToString(catalog.catalog(action == "refresh"))))
        }
        catch (e: IllegalArgumentException) {
            ExecutionFailure(e.message ?: "Invalid catalog selection")
        }
        catch (e: IllegalStateException) {
            ExecutionFailure(e.message ?: "Catalog operation failed")
        }
    }

    private fun selection(request: ExecutionRequest): List<String> =
        Json.decodeFromString(request.getSingle(CatalogConventions.selection) ?: "[]")
}
