package tech.kzen.auto.server.data.read.detection

import kotlinx.coroutines.withTimeoutOrNull
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.format.detection.DetectionCandidateIdentity
import tech.kzen.auto.common.data.format.detection.DetectionCandidateMetadata
import tech.kzen.auto.common.data.format.detection.DetectionPolicy
import tech.kzen.auto.common.data.format.detection.FormatDetectionCacheKey
import tech.kzen.auto.common.data.format.detection.FormatHintClass
import tech.kzen.auto.common.data.format.detection.FormatHintMetadata
import tech.kzen.auto.common.data.format.detection.NormalizedFormatHints
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.plugin.api.data.ReaderProbeRequest
import tech.kzen.auto.plugin.api.data.ReaderProbeResult
import tech.kzen.auto.plugin.api.data.ReaderProbeObserver
import tech.kzen.auto.plugin.api.data.ReaderProbeStrength
import tech.kzen.auto.server.data.read.ReaderCapabilityRegistry
import tech.kzen.auto.server.data.read.SuccessfulFormatResolutionCache
import tech.kzen.auto.server.objects.datasource.format.ConfiguredRecordFormatRegistry
import tech.kzen.lib.common.util.ImmutableByteArray
import kotlin.time.TimeSource


class AutomaticFormatResolver(
    private val formats: ConfiguredRecordFormatRegistry,
    private val readers: ReaderCapabilityRegistry,
    private val samples: DetectionSampleAcquirer,
    private val cache: SuccessfulFormatResolutionCache = SuccessfulFormatResolutionCache(),
    private val policyOverride: DetectionPolicy? = null,
    private val observer: AutomaticFormatResolverObserver = AutomaticFormatResolverObserver.none
) {
    suspend fun resolve(request: FormatResolutionRequest): FormatResolutionResult {
        val resolutionStarted = TimeSource.Monotonic.markNow()
        val recordsByCandidate = linkedMapOf<String, Int>()
        val hints = effectiveHints(request)
        val effectiveRequest = request.copy(hints = hints)
        val hintMetadata = formats.hintMetadata()
        val policy = policyOverride ?: DetectionPolicy.default(hintMetadata)
        val candidates = formats.candidates(effectiveRequest)
        val identities = candidates.map(DetectionCandidateIdentity::of)
        val probeIdentities = candidates.map { it.resolvedRead.reader }.distinct().map { reader ->
            "${reader.namespace}/${reader.name}/${reader.compatibility}"
        }

        val expectedFingerprint = request.expectedFingerprint
        if (expectedFingerprint != null) {
            val warmKey = FormatDetectionCacheKey.of(
                request.ref, expectedFingerprint, expectedFingerprint,
                hints, request.explicitEncoding, identities, probeIdentities, policy)
            cache.get(warmKey)?.let {
                observe(
                    request.ref, it, FormatDetectionCacheState.WarmBeforeAcquisition,
                    null, recordsByCandidate, resolutionStarted)
                return it
            }
        }

        request.budget.acquireColdPart().use { permit ->
            var acquiredSample: AcquiredDetectionSample? = null
            val completed = withTimeoutOrNull(policy.timeoutMillis) {
                val started = TimeSource.Monotonic.markNow()
                val sample = samples.acquire(effectiveRequest, policy, started)
                acquiredSample = sample
                request.budget.chargeDecodedBytes(sample.bytes.size)
                val key = FormatDetectionCacheKey.of(
                    request.ref, request.expectedFingerprint, sample.observedFingerprint,
                    hints, request.explicitEncoding, identities, probeIdentities, policy)
                val cached = cache.get(key)
                if (cached != null) {
                    return@withTimeoutOrNull Completed(
                        cached, FormatDetectionCacheState.WarmAfterAcquisition)
                }

                val hint = classify(hints, hintMetadata)
                val decoded = decodeSample(request, sample, hint)
                val result = if (hint?.hintClass == FormatHintClass.SemanticText) {
                    textResult(
                        effectiveRequest, sample, decoded,
                        FormatResolutionBasis.Extension,
                        "The ${hints.filenameExtension ?: hints.mediaType} hint identifies semantic text",
                        null)
                }
                else {
                    resolveStructured(
                        effectiveRequest, policy, candidates, hint, sample, decoded,
                        started, recordsByCandidate)
                }
                cache.put(key, result)
                Completed(result, FormatDetectionCacheState.Cold)
            }
            val result = completed?.result ?: throw timeout(policy)
            permit.completeSuccess()
            observe(
                request.ref, result, completed.cacheState,
                acquiredSample, recordsByCandidate, resolutionStarted)
            return result
        }
    }


    private suspend fun resolveStructured(
        request: FormatResolutionRequest,
        policy: DetectionPolicy,
        candidates: List<DetectionCandidateMetadata>,
        hint: FormatHintMetadata?,
        sample: AcquiredDetectionSample,
        decoded: DecodedDetectionSample,
        started: kotlin.time.TimeMark,
        recordsByCandidate: MutableMap<String, Int>
    ): FormatResolutionResult {
        val eligible = when (hint?.hintClass) {
            FormatHintClass.StructuredFamily -> candidates.filter { candidate ->
                request.hints.filenameExtension in candidate.exactExtensions ||
                    hint.structuredFamily in candidate.compatibleStructuredFamilies
            }
            FormatHintClass.SemanticText -> emptyList()
            else -> candidates
        }
        val matches = mutableListOf<ProbeMatch>()
        val rejections = mutableListOf<String>()
        for (candidate in eligible) {
            if (started.elapsedNow().inWholeMilliseconds >= policy.timeoutMillis) {
                throw FormatDetectionException(
                    tech.kzen.auto.common.data.format.detection.FormatDetectionFailureCategory.Timeout,
                    "Format detection exceeded ${policy.timeoutMillis} ms")
            }
            val probe = requireNotNull(readers.probeFor(candidate.resolvedRead.reader))
            val candidateConfig = readers.decodeValidateCanonicalize(candidate.resolvedRead)
            val admittedHints = if (hint?.hintClass == FormatHintClass.StructuredFamily) {
                request.hints
            }
            else {
                NormalizedFormatHints.empty
            }
            val structuredHint = hint?.hintClass == FormatHintClass.StructuredFamily
            val exactExtension = structuredHint &&
                request.hints.filenameExtension in candidate.exactExtensions
            val probeObserver = ReaderProbeObserver { count ->
                require(count >= 0) { "Probe record consideration count must not be negative" }
                if (count > 0) {
                    synchronized(recordsByCandidate) {
                        recordsByCandidate[candidate.formatReference] =
                            recordsByCandidate.getOrDefault(candidate.formatReference, 0) + count
                    }
                }
            }
            when (val outcome = probe.probe(ReaderProbeRequest(
                candidateConfig,
                admittedHints,
                ImmutableByteArray.copyOf(sample.bytes),
                decoded.characterViews,
                sample.endOfInput,
                policy,
                structuredHint,
                exactExtension,
                candidate.automaticAdjustments,
                probeObserver))) {
                ReaderProbeResult.NoMatch -> Unit
                is ReaderProbeResult.Rejected -> rejections += outcome.reason
                is ReaderProbeResult.Matched -> {
                    val capability = readers.resolve(candidate.resolvedRead.reader)
                    val config = capability.encode(outcome.canonicalConfig)
                    matches += ProbeMatch(
                        candidate,
                        outcome.strength,
                        outcome.evidence,
                        ResolvedReadSpec(candidate.resolvedRead.reader, listOf(sample.coding), config))
                }
            }
        }

        if (matches.isEmpty()) {
            decoded.textFailure?.let { throw it }
            if (hint?.hintClass == FormatHintClass.StructuredFamily) {
                val reason = rejections.firstOrNull()
                    ?: "No installed format validated the structured input"
                throw FormatDetectionException(
                    tech.kzen.auto.common.data.format.detection.FormatDetectionFailureCategory.Resolution,
                    reason)
            }
            return textResult(
                request,
                sample,
                decoded,
                FormatResolutionBasis.Fallback,
                "No structured format matched the bounded sample",
                if (hint?.hintClass == FormatHintClass.GenericText) null
                else "No structured format matched; input was preserved as Plain text")
        }

        val strongest = matches.maxOf { it.strength.ordinal }
        val finalists = matches.filter { it.strength.ordinal == strongest }
        val bySpec = finalists.groupBy { it.spec.digest().asString() }
        if (bySpec.size > 1) {
            if (hint?.hintClass == FormatHintClass.StructuredFamily || decoded.textFailure != null) {
                throw FormatDetectionException(
                    tech.kzen.auto.common.data.format.detection.FormatDetectionFailureCategory.Resolution,
                    "More than one installed format matched the structured input equally")
            }
            return textResult(
                request,
                sample,
                decoded,
                FormatResolutionBasis.Fallback,
                "Multiple structured formats matched equally, so the text was preserved",
                "Structured detection was ambiguous; input was preserved as Plain text")
        }

        val winner = bySpec.values.single().minBy { it.candidate.formatReference }
        val label = formats.catalog().formats.firstOrNull {
            it.reference == winner.candidate.formatReference
        }?.label ?: winner.candidate.formatReference.substringAfterLast('#')
        val dialectWarning = if (
            hint?.hintClass == FormatHintClass.StructuredFamily &&
            request.hints.filenameExtension !in winner.candidate.exactExtensions
        ) {
            "The filename identified a structured family; the sample validated a compatible dialect"
        }
        else null
        return FormatResolutionResult(winner.spec, FormatResolutionDetail(
            request.ref,
            winner.candidate.formatReference,
            label,
            FormatSelectionKind.Automatic,
            if (winner.strength == ReaderProbeStrength.ExtensionValidated) {
                FormatResolutionBasis.Extension
            }
            else {
                FormatResolutionBasis.Content
            },
            winner.evidence,
            combineWarnings(decoded.warning, dialectWarning),
            resolvedEncoding = decoded.characterViews.singleOrNull()?.encoding,
            columnsLocked = winner.candidate.columnsLocked))
    }


    /**
     * Text views of the sample. A sample that is not text under any permitted encoding is still probed as bytes
     * unless text was demanded (an explicit encoding, or a hint that only ever means text): a binary reader such
     * as a feed decoder matches on its framing, never on a filename alone, and the text failure is what the user
     * sees if nothing matched.
     */
    private fun decodeSample(
        request: FormatResolutionRequest,
        sample: AcquiredDetectionSample,
        hint: FormatHintMetadata?
    ): DecodedDetectionSample {
        val textDemanded = request.explicitEncoding != null ||
            hint?.hintClass == FormatHintClass.SemanticText
        return try {
            StrictCharacterSampleDecoder.decode(
                sample.bytes,
                sample.endOfInput,
                request.explicitEncoding,
                hint?.hintClass == FormatHintClass.StructuredFamily ||
                    hint?.hintClass == FormatHintClass.GenericText)
        }
        catch (failure: FormatDetectionException) {
            if (textDemanded) throw failure
            DecodedDetectionSample(emptyList(), null, failure)
        }
    }


    private suspend fun textResult(
        request: FormatResolutionRequest,
        sample: AcquiredDetectionSample,
        decoded: DecodedDetectionSample,
        basis: FormatResolutionBasis,
        reason: String,
        warning: String?
    ): FormatResolutionResult {
        val encoding = decoded.characterViews.single().encoding
        val configured = formats.textFallback(request.copy(explicitEncoding = encoding))
        return configured.copy(
            resolvedRead = configured.resolvedRead.copy(contentCodings = listOf(sample.coding)),
            detail = configured.detail.copy(
                selection = FormatSelectionKind.Automatic,
                basis = basis,
                reason = reason,
                warning = combineWarnings(decoded.warning, warning),
                resolvedEncoding = encoding))
    }


    private fun classify(
        hints: NormalizedFormatHints,
        metadata: List<FormatHintMetadata>
    ): FormatHintMetadata? {
        val matches = metadata.filter { hint ->
            hints.filenameExtension in hint.extensions || hints.mediaType in hint.mediaTypes
        }.distinct()
        if (matches.isEmpty()) return null
        val meanings = matches.map { it.hintClass to it.structuredFamily }.distinct()
        require(meanings.size == 1) {
            "Format hint '${hints.filenameExtension ?: hints.mediaType}' has conflicting classifications"
        }
        return matches.first()
    }


    private fun effectiveHints(request: FormatResolutionRequest): NormalizedFormatHints {
        val supplied = request.hints.filenameExtension
        val filename = request.ref.id.substringBefore('?').substringBefore('#')
        val gzipSuffix = filename.endsWith(".gz", ignoreCase = true)
        val extension = if (supplied == "gz" || (supplied == null && gzipSuffix)) {
            filename.dropLast(3)
                .substringAfterLast('.', "").takeIf(String::isNotEmpty)
        }
        else supplied
        return NormalizedFormatHints.of(extension, request.hints.mediaType, request.hints.providerHints)
    }


    private fun combineWarnings(first: String?, second: String?): String? =
        listOfNotNull(first, second).distinct().joinToString("; ").takeIf(String::isNotEmpty)


    private fun observe(
        ref: tech.kzen.auto.common.data.model.DataRef,
        result: FormatResolutionResult,
        cacheState: FormatDetectionCacheState,
        sample: AcquiredDetectionSample?,
        recordsByCandidate: Map<String, Int>,
        started: kotlin.time.TimeMark
    ) {
        val recordSnapshot = synchronized(recordsByCandidate) {
            recordsByCandidate.toSortedMap()
        }
        val observation = AutomaticFormatResolutionObservation(
            ref,
            result,
            cacheState,
            sample?.bytes?.size ?: 0,
            sample?.acquisitionCodings.orEmpty(),
            recordSnapshot,
            started.elapsedNow().inWholeNanoseconds)
        try {
            observer.completed(observation)
        }
        catch (_: Exception) {
            // An optional measurement sink cannot change format-resolution semantics.
        }
    }


    private fun timeout(policy: DetectionPolicy): FormatDetectionException = FormatDetectionException(
        tech.kzen.auto.common.data.format.detection.FormatDetectionFailureCategory.Timeout,
        "Format detection exceeded ${policy.timeoutMillis} ms")


    private data class ProbeMatch(
        val candidate: DetectionCandidateMetadata,
        val strength: ReaderProbeStrength,
        val evidence: String,
        val spec: ResolvedReadSpec
    )

    private data class Completed(
        val result: FormatResolutionResult,
        val cacheState: FormatDetectionCacheState
    )
}
