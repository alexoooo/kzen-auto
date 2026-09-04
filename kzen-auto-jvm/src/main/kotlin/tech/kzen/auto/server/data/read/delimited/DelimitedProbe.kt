package tech.kzen.auto.server.data.read.delimited

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.common.data.read.HeaderReadSpec
import tech.kzen.auto.common.data.read.ReadOperationalPolicy
import tech.kzen.auto.common.data.read.RecordFramingSpec
import tech.kzen.auto.plugin.api.data.ReaderProbeRequest
import tech.kzen.auto.plugin.api.data.ReaderProbeResult
import tech.kzen.auto.plugin.api.data.ReaderProbeStrength
import tech.kzen.auto.plugin.api.data.StrictCharacterView
import tech.kzen.auto.server.data.content.SequentialCharacterContent
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.Charset
import kotlin.time.TimeSource


internal object DelimitedProbe {
    private val identifierLabel = Regex("[A-Za-z_][A-Za-z0-9 _.-]*")


    suspend fun probe(request: ReaderProbeRequest): ReaderProbeResult {
        val candidate = request.candidateConfig as? DelimitedReadConfig
            ?: throw IllegalArgumentException("Delimited reader config expected")
        val job = currentCoroutineContext()[Job]
        val started = TimeSource.Monotonic.markNow()
        val checkpoint = {
            job?.ensureActive()
            if (started.elapsedNow().inWholeMilliseconds >= request.policy.timeoutMillis) {
                throw tech.kzen.auto.server.data.read.detection.FormatDetectionException(
                    tech.kzen.auto.common.data.format.detection.FormatDetectionFailureCategory.Timeout,
                    "Format detection exceeded ${request.policy.timeoutMillis} ms")
            }
        }
        val extensionSelected = request.structuredHint
        val rejections = mutableListOf<String>()
        var sawValidWeakSample = false
        var remainingRecords = request.policy.maximumLogicalRecords

        val views = permittedViews(request, candidate)
        if (views.isEmpty()) {
            return if (extensionSelected) {
                ReaderProbeResult.Rejected("The detected character encoding does not satisfy the configured format")
            }
            else ReaderProbeResult.NoMatch
        }
        viewLoop@ for (view in views) {
            for (framing in framingCandidates(candidate.framing, request.allowCanonicalAdjustments)) {
                if (remainingRecords == 0) break@viewLoop
                val configured = if (request.allowCanonicalAdjustments) {
                    candidate.copy(
                        framing = framing,
                        characters = candidate.characters.copy(
                            charset = view.encoding,
                            bom = "permit",
                            malformed = "report",
                            unmappable = "report"))
                }
                else {
                    candidate
                }
                try {
                    val matched = probeView(
                        view, configured, extensionSelected,
                        request.exactExtension, request.endOfInput,
                        remainingRecords,
                        request.allowCanonicalAdjustments,
                        { count ->
                            require(count in 0..remainingRecords) {
                                "Delimited probe exceeded its remaining logical-record allowance"
                            }
                            remainingRecords -= count
                            request.observer.completeLogicalRecordsConsidered(count)
                        },
                        checkpoint)
                    if (matched != null) return matched
                    sawValidWeakSample = true
                }
                catch (failure: DelimitedReadException) {
                    rejections += failure.message ?: failure.category
                }
            }
        }

        return if (extensionSelected && rejections.isNotEmpty() && !sawValidWeakSample) {
            ReaderProbeResult.Rejected(rejections.first())
        }
        else {
            ReaderProbeResult.NoMatch
        }
    }


    private fun probeView(
        view: StrictCharacterView,
        candidate: DelimitedReadConfig,
        extensionSelected: Boolean,
        relaxedExtensionEvidence: Boolean,
        endOfInput: Boolean,
        maximumRecords: Int,
        allowCanonicalAdjustments: Boolean,
        recordsConsidered: (Int) -> Unit,
        checkpoint: () -> Unit
    ): ReaderProbeResult.Matched? {
        val samplingConfig = if (
            allowCanonicalAdjustments && !extensionSelected && candidate.schema == null
        ) {
            candidate.copy(header = HeaderReadSpec("infer-labels", candidate.header.mapping))
        }
        else {
            candidate
        }
        val sampled = parse(
            view.text, samplingConfig, endOfInput, maximumRecords, recordsConsidered, checkpoint)
        if (sampled.recordCount == 0 || sampled.width <= 1) return null
        if (!relaxedExtensionEvidence && sampled.recordCount < 2) return null

        val resolved = if (
            allowCanonicalAdjustments && !extensionSelected && candidate.schema == null
        ) {
            val header = if (hasHeaderEvidence(sampled.rows)) "present" else "infer-labels"
            candidate.copy(
                framing = samplingConfig.framing,
                characters = samplingConfig.characters,
                header = HeaderReadSpec(header, candidate.header.mapping))
        }
        else {
            samplingConfig
        }
        val canonical = ConfiguredDelimitedReaderCapability.canonicalize(resolved) as DelimitedReadConfig
        val strength = if (extensionSelected) {
            ReaderProbeStrength.ExtensionValidated
        }
        else {
            ReaderProbeStrength.ContentStrong
        }
        val headerEvidence = when (canonical.header.policy) {
            "present" -> "; first record validated as a header"
            "infer-labels" -> "; row one retained with positional labels"
            else -> ""
        }
        return ReaderProbeResult.Matched(
            strength,
            canonical,
            "${sampled.recordCount} complete ${if (sampled.recordCount == 1) "record" else "records"} " +
                "with ${sampled.width} consistent ${if (sampled.width == 1) "field" else "fields"}" +
                headerEvidence)
    }


    private fun parse(
        text: String,
        config: DelimitedReadConfig,
        endOfInput: Boolean,
        maximumRecords: Int,
        recordsConsidered: (Int) -> Unit,
        checkpoint: () -> Unit
    ): SampledRecords {
        val policy = ReadOperationalPolicy(
            maximumRecordCharacters = maxOf(text.length, 1),
            maximumFieldCharacters = maxOf(text.length, 1),
            maximumFields = 10_000)
        var recordCount = 0
        try {
            ConfiguredDelimitedReader.openSample(
                StringCharacterContent(text), config, policy,
                DelimitedReadContext("format-detection"), endOfInput, checkpoint).use { reader ->
                recordCount = if (config.header.policy == "present" && reader.observedDuringOpen > 0) 1 else 0
                val rows = mutableListOf<List<String>>()
                while (recordCount < maximumRecords) {
                    val record = reader.read() ?: break
                    rows += record.backing.toList()
                    recordCount++
                }
                val width = (reader.contract.structural as
                    tech.kzen.lib.common.exec.data.type.DataType.Record).fields.size
                return SampledRecords(recordCount, width, rows)
            }
        }
        finally {
            recordsConsidered(recordCount)
        }
    }


    private fun hasHeaderEvidence(rows: List<List<String>>): Boolean {
        if (rows.size < 2) return false
        val first = rows.first()
        if (first.any(String::isEmpty) || first.distinct().size != first.size ||
            first.any { !identifierLabel.matches(it) }) return false

        return first.indices.any { column ->
            val below = rows.drop(1).map { it[column] }.filter(String::isNotEmpty)
            if (below.isEmpty()) return@any false
            val classification = classify(below.first()) ?: return@any false
            below.all { classify(it) == classification } && classify(first[column]) != classification
        }
    }


    private fun classify(value: String): ValueClass? {
        if (value == "true" || value == "false") return ValueClass.Boolean
        if (runCatching { BigInteger(value) }.isSuccess) return ValueClass.Integer
        if (runCatching { BigDecimal(value) }.isSuccess) return ValueClass.Decimal
        return null
    }


    private fun framingCandidates(
        configured: RecordFramingSpec,
        allowCanonicalAdjustments: Boolean
    ): List<RecordFramingSpec> = if (allowCanonicalAdjustments) {
        listOf(
            configured,
            RecordFramingSpec(if (configured.separator == "lf") "crlf" else "lf")
        ).distinct()
    }
    else {
        listOf(configured)
    }


    private fun permittedViews(
        request: ReaderProbeRequest,
        candidate: DelimitedReadConfig
    ): List<StrictCharacterView> {
        if (request.allowCanonicalAdjustments) return request.characterViews
        val bom = detectedBom(request.sample.toByteArray())
        val policy = candidate.characters.bom
        if ((policy == "require" || policy == "detect") && bom == null) return emptyList()
        if (policy == "forbid" && bom != null) return emptyList()
        val configured = Charset.forName(candidate.characters.charset)
        return request.characterViews.filter { view ->
            val viewed = Charset.forName(view.encoding)
            (configured == viewed || configured.name().equals("UTF-16", ignoreCase = true)) &&
                (bom == null || bom == viewed)
        }
    }


    private fun detectedBom(bytes: ByteArray): Charset? = when {
        bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() &&
            bytes[2] == 0xbf.toByte() -> Charsets.UTF_8
        bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> Charsets.UTF_16BE
        bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> Charsets.UTF_16LE
        else -> null
    }


    private data class SampledRecords(
        val recordCount: Int,
        val width: Int,
        val rows: List<List<String>>
    )

    private enum class ValueClass { Boolean, Integer, Decimal }


    private class StringCharacterContent(private val text: String): SequentialCharacterContent {
        override val resolvedCharsetName = "in-memory"
        override val inspectionRecordLimit = Long.MAX_VALUE
        private var position = 0

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            if (position == text.length) return -1
            val count = minOf(length, text.length - position)
            text.toCharArray(buffer, offset, position, position + count)
            position += count
            return count
        }

        override fun close() = Unit
    }
}
