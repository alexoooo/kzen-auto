package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.FileFormatNames
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.util.digest.Digest


/** Each file is read in the registered format its name selects ([FileFormatNames]); its content is never read. */
@Reflect
class AutomaticFormat(
    override val title: String,
    override val extensions: List<String>,
    override val catalogVisible: Boolean,
    @Service private val formats: ConfiguredRecordFormatRegistry
): ConfiguredRecordFormat {
    override val selectionKind: FormatSelectionKind = FormatSelectionKind.Automatic

    override suspend fun resolve(request: FormatResolutionRequest): FormatResolutionResult =
        formats.resolveByName(request)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun resolvedRead(ref: DataRef): ResolvedReadSpec {
        throw UnsupportedOperationException(
            "Automatic format resolution requires the registered formats and cannot be resolved statically")
    }

    override fun declaredShape(): DataShape? = null

    override fun declaredShape(
        fileName: String,
        configuredFormats: Lazy<List<ConfiguredRecordFormat>>
    ): DataShape? =
        (FileFormatNames.choose(fileName, configuredFormats.value) as? FileFormatNames.Choice.Named)
            ?.format
            ?.declaredShape()

    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(title)
    }
}
