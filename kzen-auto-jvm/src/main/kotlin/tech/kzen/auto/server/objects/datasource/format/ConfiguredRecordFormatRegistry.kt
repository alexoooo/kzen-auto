package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.format.ConfiguredFormatDetail
import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.FileFormatCatalog
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.format.FormatMaterializationRequest
import tech.kzen.auto.common.data.format.FormatMaterializationResult
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.format.detection.DetectionCandidateMetadata
import tech.kzen.auto.common.data.format.detection.FormatHintMetadata
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.server.data.TextEncodingCatalog
import tech.kzen.auto.server.data.read.ReaderCapabilityRegistry
import tech.kzen.auto.server.data.read.detection.FilenameDetection
import tech.kzen.auto.server.data.write.RecordWriterCapabilityRegistry
import tech.kzen.auto.server.service.exec.ExecutionGraphErrors
import tech.kzen.auto.server.service.exec.GraphInstanceCache
import tech.kzen.auto.server.service.exec.ObjectInstanceAttempt
import tech.kzen.auto.server.service.exec.ServerGraphDefinition
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore


class ConfiguredRecordFormatRegistry(
    private val graphStore: LocalGraphStore,
    private val graphInstanceCache: GraphInstanceCache,
    private val readerCapabilities: ReaderCapabilityRegistry,
    private val writerCapabilities: RecordWriterCapabilityRegistry
): ConfiguredRecordFormatLookup {
    suspend fun catalog(): FileFormatCatalog {
        val graphNotation = graphStore.graphDefinition().graphStructure.graphNotation
        return FileFormatCatalog(
            registeredFormats().map { registered ->
                val authoring = registered.format.authoringCapabilityIdentity
                    ?.let(readerCapabilities::authoringFor)
                val reference = registered.reference
                val inProject = reference.documentPath.startsWith(NotationConventions.mainDocumentNesting)
                ConfiguredFormatDetail(
                    reference.asString(),
                    if (inProject) projectLabel(graphNotation, reference) else registered.format.title,
                    registered.format.extensions,
                    registered.format.authoringCapabilityIdentity,
                    registered.format.overrideEditorReference,
                    authoring != null,
                    authoring?.supportsColumnLocking == true,
                    registered.format.selectionKind == FormatSelectionKind.Explicit &&
                        registered.format.readsContent,
                    reference.documentPath.name.value.takeIf { inProject },
                    writerCapabilities.writerFor(registered.format) != null)
            },
            TextEncodingCatalog.available())
    }


    // A project format's own title, else its object name: the title it inherits names its format type ("Delimited"),
    // which would make it indistinguishable from a built-in in the list
    private fun projectLabel(graphNotation: GraphNotation, reference: ObjectLocation): String =
        graphNotation.directAttribute(reference, AutoConventions.titleAttributePath)
            ?.asString()
            ?.takeIf { it.isNotBlank() }
            ?: reference.objectPath.name.value


    suspend fun resolve(
        reference: String,
        request: FormatResolutionRequest
    ): FormatResolutionResult = preflight(reference).resolve(request)


    override suspend fun preflight(reference: String): ConfiguredRecordFormatPreflight {
        val registered = registeredFormat(ObjectLocation.parse(reference))
        return ConfiguredRecordFormatPreflight(registered.reference.asString(), registered.format)
    }


    // The injected instance is kept: what is read stays the snapshot its owner was built from, only the reference
    // comes from the graph
    override suspend fun preflight(
        format: ConfiguredRecordFormat,
        injectedBy: AttributeLocation
    ): ConfiguredRecordFormatPreflight? {
        val graphNotation = graphStore.graphDefinition().graphStructure.graphNotation
        val owner = injectedBy.objectLocation
        if (owner !in graphNotation.coalesce) {
            return null
        }
        val reference = graphNotation.firstAttribute(owner, injectedBy.attributePath)
            ?.asString()
            ?.let(ObjectReference::parse)
            ?: return null
        val location = graphNotation.coalesce.locateOptional(reference, ObjectReferenceHost.ofLocation(owner))
            ?: return null
        return ConfiguredRecordFormatPreflight(location.asString(), format)
    }


    suspend fun materialize(
        reference: String,
        request: FormatMaterializationRequest
    ): FormatMaterializationResult {
        val preflight = preflight(reference)
        val authoringIdentity = preflight.format.authoringCapabilityIdentity
            ?: throw IllegalArgumentException("${preflight.format.title} does not support quick correction")
        val authoring = readerCapabilities.authoringFor(authoringIdentity)
            ?: throw IllegalArgumentException("${preflight.format.title} authoring capability is unavailable")
        require(request.baseFormatReference == preflight.reference) {
            "Materialization base format does not match the registered format"
        }
        require(request.observedSchema == null || authoring.supportsColumnLocking) {
            "${preflight.format.title} does not support locking observed columns"
        }
        return authoring.materialize(request)
    }


    suspend fun candidates(request: FormatResolutionRequest): List<DetectionCandidateMetadata> {
        return registeredFormats().mapNotNull { registered ->
            val format = registered.format
            if (!format.automaticDetectionCandidate) {
                return@mapNotNull null
            }
            // A format that refuses the request outright (a binary one under an explicit text encoding) is simply
            // not a candidate for it; the refusal must not fail detection for the formats that do apply.
            val resolved = try {
                format.resolve(request).resolvedRead
            }
            catch (_: IllegalArgumentException) {
                return@mapNotNull null
            }
            if (readerCapabilities.probeFor(resolved.reader) == null) {
                return@mapNotNull null
            }
            DetectionCandidateMetadata(
                registered.reference.asString(),
                format.digest(),
                FilenameDetection.exactExtensions(format),
                FilenameDetection.structuredFamilies(format),
                resolved,
                format.automaticDetectionTemplate,
                format.authoringCapabilityIdentity,
                format.overrideEditorReference,
                format.columnsLocked)
        }.sortedWith(compareBy(DetectionCandidateMetadata::formatReference)
            .thenBy { it.formatDigest.asString() })
    }


    suspend fun hintMetadata(): List<FormatHintMetadata> = registeredFormats()
        .flatMap { it.format.hintMetadata }
        .distinct()
        .sortedBy { it.digest().asString() }


    suspend fun textFallback(request: FormatResolutionRequest): FormatResolutionResult {
        val fallbacks = registeredFormats().filter { it.format.automaticTextFallback }
        check(fallbacks.size == 1) {
            "Exactly one automatic text fallback must be registered; found ${fallbacks.size}"
        }
        val fallback = fallbacks.single()
        val result = fallback.format.resolve(request)
        return result.copy(detail = result.detail.copy(concreteFormatReference = fallback.reference.asString()))
    }


    private suspend fun registeredFormats(): List<RegisteredConfiguredFormat> =
        availableFormats().filter { it.format.catalogVisible }


    private suspend fun availableFormats(): List<RegisteredConfiguredFormat> {
        val references = formatLocations(graphStore.graphDefinition().transitiveSuccessful)
        val registered = mutableListOf<RegisteredConfiguredFormat>()
        for (reference in references) {
            val candidate = registeredFormat(reference)
            registered.add(candidate)
        }
        return registered
    }


    private suspend fun registeredFormat(reference: ObjectLocation): RegisteredConfiguredFormat {
        val definitionAttempt = graphStore.graphDefinition()
        val instanceAttempt = graphInstanceCache.tryObjectInstance(
            ServerGraphDefinition.of(definitionAttempt), reference)
        val instance = (instanceAttempt as? ObjectInstanceAttempt.Created)
            ?.objectInstance
            ?.reference
            ?: throw IllegalArgumentException(
                ExecutionGraphErrors.describe(reference, definitionAttempt, instanceAttempt))
        val format = instance as? ConfiguredRecordFormat
            ?: throw IllegalArgumentException("Not a configured record format: $reference")
        return RegisteredConfiguredFormat(reference, format)
    }


    private data class RegisteredConfiguredFormat(
        val reference: ObjectLocation,
        val format: ConfiguredRecordFormat
    )


    companion object {
        val configuredFormatMarker = ObjectLocation.parse(
            "auto-jvm/datasource/configured-delimited-format.yaml#ConfiguredRecordFormat")


        /** The concrete, server-allowed format objects of [definition], in reference order. */
        fun formatLocations(definition: GraphDefinition): List<ObjectLocation> {
            val notation = definition.graphStructure.graphNotation
            val availableDefinitions = ServerGraphDefinition.of(definition).objectDefinitions
            return notation.objectLocations
                .asSequence()
                .filter { it != configuredFormatMarker }
                .filter { configuredFormatMarker in notation.inheritanceChain(it) }
                .filter {
                    notation.directAttribute(it, NotationConventions.abstractAttributePath)?.asBoolean() != true
                }
                .filter { it in availableDefinitions }
                .sortedBy { it.asString() }
                .toList()
        }


        /** The formats automatic detection chooses among in the [definition] snapshot, created through [instances]. */
        fun formatsOf(
            definition: GraphDefinition,
            instances: GraphInstanceCache
        ): List<ConfiguredRecordFormat> = formatLocations(definition)
            .map { location ->
                val instance = when (val attempt = instances.tryObjectInstance(definition, location)) {
                    is ObjectInstanceAttempt.Created -> attempt.objectInstance.reference
                    is ObjectInstanceAttempt.Failed -> throw IllegalArgumentException(
                        "Unable to create configured format $location: ${attempt.failure.errorMessage}")
                    ObjectInstanceAttempt.Undefined -> error("Configured format is not defined: $location")
                }
                instance as? ConfiguredRecordFormat
                    ?: throw IllegalArgumentException("Not a configured record format: $location")
            }
            .filter { it.catalogVisible }
    }
}
