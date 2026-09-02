package tech.kzen.auto.server.objects.datasource

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.auto.common.data.api.DataSource
import tech.kzen.auto.common.data.format.FileFormatCatalog
import tech.kzen.auto.common.data.format.ConfiguredFormatDetail
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.server.data.DataOpenerLookup
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
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore


@Reflect
class DataSourceActions(
    @Service private val graphStore: LocalGraphStore,
    @Service private val graphInstanceCache: GraphInstanceCache,
    @Service private val openerLookup: DataOpenerLookup
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


    // Enumerate concrete configured formats from the same graph snapshot used for object construction.
    private suspend fun fileFormats(): FileFormatCatalog {
        val graphNotation = graphStore.graphNotation()
        val marker = ObjectLocation.parse(
            "auto-jvm/datasource/configured-delimited-format.yaml#ConfiguredRecordFormat")
        val extensionsPath = AttributePath.ofName(AttributeName("extensions"))
        val catalogVisiblePath = AttributePath.ofName(AttributeName("catalogVisible"))
        val formats = graphNotation.objectLocations.mapNotNull { location ->
            if (location == marker || marker !in graphNotation.inheritanceChain(location)) {
                return@mapNotNull null
            }
            val isAbstract = graphNotation
                .directAttribute(location, NotationConventions.abstractAttributePath)
                ?.asBoolean() == true
            if (isAbstract) return@mapNotNull null
            val catalogVisible = graphNotation.firstAttribute(location, catalogVisiblePath)?.asBoolean() != false
            if (!catalogVisible) return@mapNotNull null
            val label = graphNotation
                .firstAttribute(location, AutoConventions.titleAttributePath)
                ?.asString()
                ?: location.objectPath.name.value
            val extensions = (graphNotation.firstAttribute(location, extensionsPath) as? ListAttributeNotation)
                ?.values
                ?.mapNotNull { it.asString() }
                .orEmpty()
            ConfiguredFormatDetail(location.asString(), label, extensions)
        }.sortedBy { it.reference }

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

        val shape = try {
            openerLookup.openerFor(part.ref).inspectShape(context, part)
        }
        catch (e: IllegalArgumentException) {
            return ExecutionFailure(e.message ?: "Unable to inspect data part")
        }
        return shape?.let { ExecutionSuccess.ofValue(it.asExecutionValue()) }
            ?: ExecutionFailure("Data shape is unavailable for ${part.ref.display()}")
    }
}
