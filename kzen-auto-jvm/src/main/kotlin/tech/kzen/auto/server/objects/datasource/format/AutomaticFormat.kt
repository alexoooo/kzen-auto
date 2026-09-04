package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.server.data.read.detection.AutomaticFormatResolver
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.util.digest.Digest


@Reflect
class AutomaticFormat(
    override val title: String,
    override val extensions: List<String>,
    override val catalogVisible: Boolean,
    @Service private val resolver: AutomaticFormatResolver
): ConfiguredRecordFormat {
    override val selectionKind: FormatSelectionKind = FormatSelectionKind.Automatic
    override val automaticDetectionCandidate: Boolean = false

    override suspend fun resolve(request: FormatResolutionRequest): FormatResolutionResult =
        resolver.resolve(request)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun resolvedRead(ref: DataRef): ResolvedReadSpec {
        throw UnsupportedOperationException(
            "Automatic format resolution requires source context and cannot be resolved statically")
    }

    override fun declaredShape(): DataShape? = null

    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(title)
    }
}
