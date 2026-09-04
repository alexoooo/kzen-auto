package tech.kzen.auto.server.objects.datasource

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.auto.common.data.api.DataSource
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.format.FormatMaterializationActionRequest
import tech.kzen.auto.common.data.format.FormatMaterializationIntent
import tech.kzen.auto.common.data.format.FormatMaterializationRequest
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.schema.AuthoredRecordSchemaDraft
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.objects.datasource.format.ConfiguredRecordFormatRegistry
import tech.kzen.auto.server.objects.datasource.format.SourceLocalFormatMaterializer
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
    @Service private val configuredFormatRegistry: ConfiguredRecordFormatRegistry
): DetachedAction {
    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val action = request.getSingle(DataSourceConventions.actionParameter)
            ?: return ExecutionFailure("Missing data source action")

        // Answered before a source is resolved: this one describes what the server has installed, not what one
        // configured source holds, so it stays available on a source that cannot currently be instantiated —
        // which is exactly when someone is in its editor picking a format.
        if (action == DataSourceConventions.fileFormatsAction) {
            return ExecutionSuccess.ofValue(ExecutionValue.of(configuredFormatRegistry.catalog().asCollection()))
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

            DataSourceConventions.resolveFileAction ->
                resolveFile(source, context, request)

            DataSourceConventions.materializeFormatAction ->
                materializeFormat(sourceLocation, source, context, request)

            DataSourceConventions.shapeAction -> shape(source, context, request)

            else -> ExecutionFailure("Unknown data source action: $action")
        }
    }


    private suspend fun resolveFile(
        source: DataSource,
        context: DesignDataContext,
        request: ExecutionRequest
    ): ExecutionResult {
        val fileSource = source as? FileResolutionDataSource
            ?: return ExecutionFailure("Data source does not support per-file resolution")
        val result = fileSource.resolveFile(context, fileEntry(request))
        return ExecutionSuccess.ofValue(result.asExecutionValue())
    }


    private suspend fun materializeFormat(
        sourceLocation: ObjectLocation,
        source: DataSource,
        context: DesignDataContext,
        request: ExecutionRequest
    ): ExecutionResult {
        val fileSource = source as? FileResolutionDataSource
            ?: return ExecutionFailure("Data source does not support per-file format materialization")
        val body = request.body
            ?: return ExecutionFailure("Missing format materialization request")
        val authoringRequest = try {
            Json.decodeFromString<FormatMaterializationActionRequest>(body.toByteArray().decodeToString())
        }
        catch (failure: Exception) {
            return ExecutionFailure(
                "Invalid format materialization request: ${failure.message ?: failure::class.simpleName}")
        }

        val current = fileSource.resolveFile(context, fileEntry(request))
        val currentPart = current.manifest.units.singleOrNull()?.parts?.singleOrNull()
            ?: return ExecutionFailure("File preview no longer resolves to exactly one data part")
        val currentDetail = current.resolutionDetails.singleOrNull()
            ?: return ExecutionFailure("File preview no longer has exactly one format resolution detail")
        if (currentPart.ref != authoringRequest.part.ref ||
            currentPart.expectedFingerprint != authoringRequest.part.expectedFingerprint ||
            currentPart.resolvedRead != authoringRequest.part.resolvedRead) {
            return ExecutionFailure("The selected file changed after preview; resolve it again")
        }
        if (currentDetail.concreteFormatReference != authoringRequest.concreteFormatReference) {
            return ExecutionFailure("The selected file format changed after preview; resolve it again")
        }
        if (authoringRequest.intent != FormatMaterializationIntent.Override &&
                currentDetail.selection != FormatSelectionKind.Automatic) {
            return ExecutionFailure("Repeatability actions require a current Automatic format result")
        }
        if (authoringRequest.intent != FormatMaterializationIntent.Override &&
                authoringRequest.overrides.isNotEmpty()) {
            return ExecutionFailure("Repeatability actions do not accept quick-control overrides")
        }

        val observedSchema = if (authoringRequest.intent == FormatMaterializationIntent.LockColumns) {
            if (currentDetail.columnsLocked) {
                return ExecutionFailure("The selected file already has locked columns")
            }
            val shape = try {
                openerLookup.openerFor(currentPart.ref).inspectShape(context, currentPart)
            }
            catch (failure: IllegalArgumentException) {
                return ExecutionFailure(failure.message ?: "Unable to inspect the selected file columns")
            }
            val contract = shape?.itemType
                ?: return ExecutionFailure("The selected file does not expose an inspectable record contract")
            if (AuthoredRecordSchemaDraft.from(contract) == null) {
                return ExecutionFailure(
                    "The observed data shape cannot be represented as a locked record schema")
            }
            contract
        }
        else {
            null
        }

        val materialized = configuredFormatRegistry.materialize(
            authoringRequest.concreteFormatReference,
            FormatMaterializationRequest(
                authoringRequest.concreteFormatReference,
                currentPart.resolvedRead,
                observedSchema,
                authoringRequest.overrides))
        val result = SourceLocalFormatMaterializer.prepare(
            graphStore.graphNotation(),
            sourceLocation,
            currentPart.ref.id,
            materialized)
        return ExecutionSuccess.ofValue(result.asExecutionValue())
    }


    private fun fileEntry(request: ExecutionRequest): FileSelectionEntry {
        val location = request.getSingle(DataSourceConventions.locationParameter)
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Missing file location")
        val format = request.getSingle(DataSourceConventions.formatParameter)
            ?.takeIf(String::isNotBlank)
            ?.let(CommonPluginCoordinate::ofString)
        val encoding = request.getSingle(DataSourceConventions.encodingParameter)
            ?.takeIf(String::isNotBlank)
            ?.let(CommonDataEncodingSpec::ofString)
        return FileSelectionEntry(DataLocation.of(location), format, encoding)
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
