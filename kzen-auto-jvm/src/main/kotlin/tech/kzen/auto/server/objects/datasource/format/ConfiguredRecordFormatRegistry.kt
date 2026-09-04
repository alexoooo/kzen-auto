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
import tech.kzen.auto.server.data.TextEncodingCatalog
import tech.kzen.auto.server.data.read.ReaderCapabilityRegistry
import tech.kzen.auto.server.service.exec.ExecutionGraphErrors
import tech.kzen.auto.server.service.exec.GraphInstanceCache
import tech.kzen.auto.server.service.exec.ObjectInstanceAttempt
import tech.kzen.auto.server.service.exec.ServerGraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore


class ConfiguredRecordFormatRegistry(
    private val graphStore: LocalGraphStore,
    private val graphInstanceCache: GraphInstanceCache,
    private val readerCapabilities: ReaderCapabilityRegistry
): ConfiguredRecordFormatLookup {
    suspend fun catalog(): FileFormatCatalog = FileFormatCatalog(
        registeredFormats().map { registered ->
            val authoring = registered.format.authoringCapabilityIdentity
                ?.let(readerCapabilities::authoringFor)
            ConfiguredFormatDetail(
                registered.reference.asString(),
                registered.format.title,
                registered.format.extensions,
                registered.format.authoringCapabilityIdentity,
                registered.format.overrideEditorReference,
                authoring != null,
                authoring?.supportsColumnLocking == true,
                registered.format.selectionKind == FormatSelectionKind.Explicit)
        },
        TextEncodingCatalog.available())


    suspend fun resolve(
        reference: String,
        request: FormatResolutionRequest
    ): FormatResolutionResult = preflight(reference).resolve(request)


    override suspend fun preflight(reference: String): ConfiguredRecordFormatPreflight {
        val registered = registeredFormat(ObjectLocation.parse(reference))
        return ConfiguredRecordFormatPreflight(registered.reference.asString(), registered.format)
    }


    override suspend fun preflight(format: ConfiguredRecordFormat): ConfiguredRecordFormatPreflight? {
        val registered = availableFormats()
        val exact = registered.filter { it.format === format }
        if (exact.size == 1) {
            return exact.single().asPreflight()
        }
        require(exact.isEmpty()) { "Injected configured format has ambiguous graph identity" }

        val semantic = registered.filter {
            it.format::class == format::class && it.format.digest() == format.digest()
        }
        if (semantic.isEmpty()) {
            // Programmatically constructed and test-local formats still resolve strictly through their own
            // immutable config. With no graph coordinate there is no stable reference to publish in the catalog.
            return null
        }
        require(semantic.size == 1) {
            "Injected configured format has ambiguous value-identical graph coordinates: " +
                semantic.joinToString { it.reference.asString() }
        }
        return semantic.single().asPreflight()
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
            val resolved = format.resolve(request).resolvedRead
            if (readerCapabilities.probeFor(resolved.reader) == null) {
                return@mapNotNull null
            }
            DetectionCandidateMetadata(
                registered.reference.asString(),
                format.digest(),
                format.extensions.map(::normalizeExtension).distinct().sorted(),
                format.compatibleStructuredFamilies.map { it.trim().lowercase() }.distinct().sorted(),
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
        val notation = graphStore.graphNotation()
        val availableDefinitions = ServerGraphDefinition.of(graphStore.graphDefinition()).objectDefinitions
        val references = notation.objectLocations
            .asSequence()
            .filter { it != configuredFormatMarker }
            .filter { configuredFormatMarker in notation.inheritanceChain(it) }
            .filter {
                notation.directAttribute(it, NotationConventions.abstractAttributePath)?.asBoolean() != true
            }
            .filter { it in availableDefinitions }
            .toList()
        val registered = mutableListOf<RegisteredConfiguredFormat>()
        for (reference in references) {
            val candidate = registeredFormat(reference)
            registered.add(candidate)
        }
        return registered.sortedBy { it.reference.asString() }
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


    private fun normalizeExtension(extension: String): String =
        extension.trim().removePrefix(".").lowercase()


    private data class RegisteredConfiguredFormat(
        val reference: ObjectLocation,
        val format: ConfiguredRecordFormat
    ) {
        fun asPreflight(): ConfiguredRecordFormatPreflight =
            ConfiguredRecordFormatPreflight(reference.asString(), format)
    }


    companion object {
        val configuredFormatMarker = ObjectLocation.parse(
            "auto-jvm/datasource/configured-delimited-format.yaml#ConfiguredRecordFormat")
    }
}
