package tech.kzen.auto.server.data.read.detection

import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.detection.DetectionPolicy
import tech.kzen.auto.common.data.read.ContentCapabilityIdentity
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.server.data.content.OpenedReaderByteInput
import tech.kzen.auto.server.data.content.SequentialContentStack
import tech.kzen.auto.server.data.content.policy.ContentReadPolicy
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource


class DetectionSampleAcquirer(
    private val contentStack: SequentialContentStack
) {
    suspend fun acquire(
        request: FormatResolutionRequest,
        policy: DetectionPolicy,
        started: TimeMark = TimeSource.Monotonic.markNow()
    ): AcquiredDetectionSample {
        require(policy.maximumDecodedBytes >= magicPrefixSize) {
            "Detection byte limit must be at least $magicPrefixSize bytes"
        }
        val raw = contentStack.openBytes(
            request.context,
            request.ref,
            request.expectedFingerprint,
            listOf(ContentCodingSpec.identity),
            ContentCapabilityIdentity.sequentialBytes,
            contentPolicy(policy, started))
        val prefix = try {
            readPrefix(raw, magicPrefixSize)
        }
        catch (failure: Throwable) {
            raw.close()
            throw failure
        }
        val gzip = isGzip(prefix.bytes)
        try {
            if (hasGzipSuffix(request) && !gzip) {
                throw FormatDetectionException(
                    tech.kzen.auto.common.data.format.detection.FormatDetectionFailureCategory.Resolution,
                    "${request.ref.display()} has a .gz suffix but does not contain gzip data")
            }
            if (isZip(prefix.bytes)) {
                throw FormatDetectionException(
                    tech.kzen.auto.common.data.format.detection.FormatDetectionFailureCategory.Resolution,
                    "${request.ref.display()} is a ZIP container; select an entry before choosing a record format")
            }

            if (!gzip) {
                val collected = collect(raw, policy.maximumDecodedBytes, prefix)
                return AcquiredDetectionSample(
                    collected.bytes,
                    collected.endOfInput,
                    ContentCodingSpec.identity,
                    listOf(ContentCodingSpec.identity),
                    raw.observedFingerprint)
            }
        }
        finally {
            raw.close()
        }

        val decoded = contentStack.openBytes(
            request.context,
            request.ref,
            request.expectedFingerprint,
            listOf(ContentCodingSpec.gzip),
            ContentCapabilityIdentity.sequentialBytes,
            contentPolicy(policy, started))
        decoded.use {
            require(it.observedFingerprint == prefix.observedFingerprint) {
                "Content fingerprint changed between gzip inspection and decoding"
            }
            val collected = collect(it, policy.maximumDecodedBytes)
            return AcquiredDetectionSample(
                collected.bytes,
                collected.endOfInput,
                ContentCodingSpec.gzip,
                listOf(ContentCodingSpec.identity, ContentCodingSpec.gzip),
                it.observedFingerprint)
        }
    }


    private fun readPrefix(input: OpenedReaderByteInput, maximum: Int): Prefix {
        val bytes = ByteArray(maximum)
        var size = 0
        var endOfInput = false
        while (size < maximum) {
            val count = input.read(bytes, size, maximum - size)
            if (count < 0) {
                endOfInput = true
                break
            }
            size += count
        }
        return Prefix(bytes.copyOf(size), endOfInput, input.observedFingerprint)
    }


    private fun collect(
        input: OpenedReaderByteInput,
        maximum: Int,
        prefix: Prefix? = null
    ): Collected {
        val bytes = ByteArray(maximum)
        var size = prefix?.bytes?.size ?: 0
        prefix?.bytes?.copyInto(bytes)
        var endOfInput = prefix?.endOfInput == true
        while (size < maximum) {
            val count = input.read(bytes, size, maximum - size)
            if (count < 0) {
                endOfInput = true
                break
            }
            size += count
        }
        return Collected(bytes.copyOf(size), endOfInput)
    }


    private fun contentPolicy(policy: DetectionPolicy, started: TimeMark): ContentReadPolicy {
        val remaining = policy.timeoutMillis - started.elapsedNow().inWholeMilliseconds
        if (remaining <= 0) {
            throw FormatDetectionException(
                tech.kzen.auto.common.data.format.detection.FormatDetectionFailureCategory.Timeout,
                "Format detection exceeded ${policy.timeoutMillis} ms")
        }
        return ContentReadPolicy(
            policy.maximumDecodedBytes.toLong(),
            remaining.milliseconds,
            policy.maximumLogicalRecords.toLong())
    }


    private fun hasGzipSuffix(request: FormatResolutionRequest): Boolean =
        request.ref.id.substringBefore('?').substringBefore('#').lowercase().endsWith(".gz")

    private fun isGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

    private fun isZip(bytes: ByteArray): Boolean = bytes.size >= 4 &&
        bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
        (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte()) &&
        (bytes[3] == 0x04.toByte() || bytes[3] == 0x06.toByte() || bytes[3] == 0x08.toByte())


    private data class Prefix(
        val bytes: ByteArray,
        val endOfInput: Boolean,
        val observedFingerprint: tech.kzen.auto.common.data.read.DataContentFingerprint
    )

    private data class Collected(val bytes: ByteArray, val endOfInput: Boolean)

    companion object {
        private const val magicPrefixSize = 4
    }
}
