package tech.kzen.auto.server.data.read.archive

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.read.ContentCapabilityIdentity
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ReaderConfig
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.plugin.api.data.BlockingReaderCapability
import tech.kzen.auto.plugin.api.data.BlockingReaderProbe
import tech.kzen.auto.plugin.api.data.ReaderInspectionRequest
import tech.kzen.auto.plugin.api.data.ReaderOpenRequest
import tech.kzen.auto.plugin.api.data.ReaderProbeRequest
import tech.kzen.auto.plugin.api.data.ReaderProbeResult
import tech.kzen.auto.plugin.api.data.ReaderProbeStrength
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.lib.common.exec.data.shape.ShapeStability


/**
 * Reads a tar archive as its table of contents. Detection is by the header magic of the first member, over a
 * sample the host has already gunzipped, so `.tar`, `.tar.gz` and `.tgz` are one case here.
 */
object ArchiveListingReaderCapability: BlockingReaderCapability(), BlockingReaderProbe {
    override val identity = ReaderCapabilityIdentity("tech.kzen.auto", "archive-listing", "1")
    override val readerCompatibility: String = identity.compatibility

    val shape = DataShape(ArchiveListingCursor.contract, ShapeProvenance.Declared, ShapeStability.Stable)


    override fun decode(config: ExecutionValue): ReaderConfig {
        require(config is MapExecutionValue && config.values.isEmpty()) {
            "Archive listing reader takes no settings"
        }
        return ArchiveListingReadConfig
    }

    override fun validate(config: ReaderConfig) {
        require(config is ArchiveListingReadConfig) { "Archive listing reader config expected" }
    }

    override fun canonicalize(config: ReaderConfig): ReaderConfig {
        validate(config)
        return config
    }

    override fun encode(config: ReaderConfig): ExecutionValue {
        validate(config)
        return ArchiveListingReadConfig.asExecutionValue()
    }

    override fun requiredContent(config: ReaderConfig): ContentCapabilityIdentity {
        validate(config)
        return ContentCapabilityIdentity.sequentialBytes
    }


    override fun openBlocking(request: ReaderOpenRequest): DataCursor {
        validate(request.config)
        return ArchiveListingCursor(request.bytes, shape)
    }


    // The columns are fixed, and walking headers to confirm them would decompress past the inspection byte limit
    // on any archive whose first member is large.
    override fun inspectBlocking(request: ReaderInspectionRequest): DataShape {
        validate(request.open.config)
        return shape
    }


    override fun probeBlocking(request: ReaderProbeRequest): ReaderProbeResult {
        val sample = request.sample.toByteArray()
        if (TarArchiveInputStream.matches(sample, sample.size)) {
            request.observer.completeLogicalRecordsConsidered(1)
            return ReaderProbeResult.Matched(
                ReaderProbeStrength.ContentSignature,
                request.candidateConfig,
                "Tar member header found at the start of the content")
        }
        if (request.structuredHint) {
            return ReaderProbeResult.Rejected("Named as a tar archive but the content has no tar member header")
        }
        return ReaderProbeResult.NoMatch
    }
}
