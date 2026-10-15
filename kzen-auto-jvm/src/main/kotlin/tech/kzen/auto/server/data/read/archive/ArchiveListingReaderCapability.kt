package tech.kzen.auto.server.data.read.archive

import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.read.ContentCapabilityIdentity
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ReaderConfig
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.plugin.api.data.BlockingReaderCapability
import tech.kzen.auto.plugin.api.data.ReaderInspectionRequest
import tech.kzen.auto.plugin.api.data.ReaderOpenRequest
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.lib.common.exec.data.shape.ShapeStability


/**
 * Reads a tar archive as its table of contents. The host has already gunzipped a `.tar.gz` or `.tgz`, so all
 * three are one case here.
 */
object ArchiveListingReaderCapability: BlockingReaderCapability() {
    override val identity = ReaderCapabilityIdentity("tech.kzen.auto", "archive-listing", "1")

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


    override fun fixedShape(config: ReaderConfig): DataShape {
        validate(config)
        return shape
    }


    // The columns are fixed, and walking headers to confirm them would decompress past the inspection byte limit
    // on any archive whose first member is large.
    override fun inspectBlocking(request: ReaderInspectionRequest): DataShape {
        validate(request.open.config)
        return shape
    }
}
