package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.FileFormatNames
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.server.data.read.archive.ArchiveListingReadConfig
import tech.kzen.auto.server.data.read.archive.ArchiveListingReaderCapability
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.digest.Digest


/** A tar archive read as one record per member (name, size, modified, kind) rather than as the members' bytes. */
@Reflect
class ArchiveListingFormat(
    override val title: String,
    override val extensions: List<String>,
    override val catalogVisible: Boolean
): ConfiguredRecordFormat {
    @Suppress("OVERRIDE_DEPRECATION")
    override fun resolvedRead(ref: DataRef): ResolvedReadSpec =
        ResolvedReadSpec(
            ArchiveListingReaderCapability.identity,
            listOf(FileFormatNames.contentCoding(ref.id)),
            ArchiveListingReaderCapability.encode(ArchiveListingReadConfig))


    override fun declaredShape(): DataShape = ArchiveListingReaderCapability.shape


    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(title)
        sink.addDigestible(ArchiveListingReadConfig.asExecutionValue())
    }
}
