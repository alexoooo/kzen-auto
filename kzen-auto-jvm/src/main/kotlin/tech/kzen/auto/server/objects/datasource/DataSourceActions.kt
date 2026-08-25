package tech.kzen.auto.server.objects.datasource

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.auto.common.data.api.DataSource
import tech.kzen.auto.common.data.format.FileFormatCatalog
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.data.FileDataOpener
import tech.kzen.auto.server.data.ReportDefinitionRepository
import tech.kzen.auto.server.data.TextEncodingCatalog
import tech.kzen.auto.server.service.exec.ExecutionGraphErrors
import tech.kzen.auto.server.service.exec.GraphInstanceCache
import tech.kzen.auto.server.service.exec.ObjectInstanceAttempt
import tech.kzen.auto.server.service.exec.ServerGraphDefinition
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore


@Reflect
class DataSourceActions(
    @Service private val graphStore: LocalGraphStore,
    @Service private val graphInstanceCache: GraphInstanceCache,
    @Service private val openerLookup: DataOpenerLookup,
    @Service private val definitionRepository: ReportDefinitionRepository
): DetachedAction {
    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val action = request.getSingle(DataSourceConventions.actionParameter)
            ?: return ExecutionFailure("Missing data source action")

        // Answered before a source is resolved: this one describes what the server has installed, not what one
        // configured source holds, so it stays available on a source that cannot currently be instantiated —
        // which is exactly when someone is in its editor picking a format.
        if (action == DataSourceConventions.fileFormatsAction) {
            return ExecutionSuccess.ofValue(ExecutionValue.of(fileFormats().asCollection()))
        }

        val sourceValue = request.getSingle(DataSourceConventions.sourceParameter)
            ?: return ExecutionFailure("Missing data source")
        val sourceLocation = ObjectLocation.parse(sourceValue)
        val definitionAttempt = graphStore.graphDefinition()
        val instanceAttempt = graphInstanceCache.tryObjectInstance(
            ServerGraphDefinition.of(definitionAttempt), sourceLocation)
        val instance = (instanceAttempt as? ObjectInstanceAttempt.Created)?.objectInstance?.reference
            ?: return ExecutionFailure(
                ExecutionGraphErrors.describe(sourceLocation, definitionAttempt, instanceAttempt))
        val source = instance as? DataSource
            ?: return ExecutionFailure("Not a DataSource: $sourceLocation")

        val context = DesignDataContext(request)
        return when (action) {
            DataSourceConventions.resolveAction ->
                ExecutionSuccess.ofValue(source.resolve(context).asExecutionValue())

            DataSourceConventions.shapeAction -> shape(source, context, request)

            else -> ExecutionFailure("Unknown data source action: $action")
        }
    }


    // Scoped to the payload type FileDataOpener resolves among, so the selects offer only formats that could
    // actually read the chosen files — the same repository query the reader performs, asked ahead of time.
    private fun fileFormats(): FileFormatCatalog {
        val formats = definitionRepository
            .listMetadata()
            .filter { it.payloadType == FileDataOpener.flatRecordPayloadType }
            .map { it.toProcessorDefinerDetail() }
            .sortedBy { it.coordinate.asString() }

        return FileFormatCatalog(formats, TextEncodingCatalog.available())
    }


    private suspend fun shape(
        source: DataSource,
        context: DesignDataContext,
        request: ExecutionRequest
    ): ExecutionResult {
        val body = request.body
            ?: return ExecutionFailure("Missing data part")
        val part = try {
            Json.decodeFromString<DataPart>(body.toByteArray().decodeToString())
        }
        catch (e: Exception) {
            return ExecutionFailure("Invalid data part: ${e.message ?: e::class.simpleName}")
        }

        val shape = source.staticShape(part.role)
            ?: try {
                openerLookup.openerFor(part.ref).inspectShape(context, part)
            }
            catch (e: IllegalArgumentException) {
                return ExecutionFailure(e.message ?: "Unable to inspect data part")
            }
        return shape?.let { ExecutionSuccess.ofValue(it.asExecutionValue()) }
            ?: ExecutionFailure("Data shape is unavailable for ${part.ref.display()}")
    }
}
