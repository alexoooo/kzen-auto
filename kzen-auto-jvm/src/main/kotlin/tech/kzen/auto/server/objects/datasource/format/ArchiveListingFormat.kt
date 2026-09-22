package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.detection.FormatHintMetadata
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.ContentCodingSpec
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
    companion object {
        private const val structuredFamily = "tar"
        private val gzipSuffixes = listOf(".gz", ".tgz")
    }


    override val hintMetadata: List<FormatHintMetadata> =
        if (extensions.isEmpty()) emptyList()
        else listOf(FormatHintMetadata.structured(structuredFamily, extensions))


    @Suppress("OVERRIDE_DEPRECATION")
    override fun resolvedRead(ref: DataRef): ResolvedReadSpec {
        val name = ref.id.lowercase()
        val coding =
            if (gzipSuffixes.any(name::endsWith)) ContentCodingSpec.gzip
            else ContentCodingSpec.identity
        return ResolvedReadSpec(
            ArchiveListingReaderCapability.identity,
            listOf(coding),
            ArchiveListingReaderCapability.encode(ArchiveListingReadConfig))
    }


    override fun declaredShape(): DataShape = ArchiveListingReaderCapability.shape


    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(title)
        sink.addDigestible(ArchiveListingReadConfig.asExecutionValue())
    }
}
