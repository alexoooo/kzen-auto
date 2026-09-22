package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.detection.FormatHintMetadata
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.digest.Digest


/**
 * The opaque end of the format ladder: every selected file is handed on as a file (to be copied, compressed,
 * extracted) and none is opened, whatever it holds. Chosen for a whole source, never detected.
 */
@Reflect
class WholeFileFormat(
    override val title: String,
    override val extensions: List<String>,
    override val catalogVisible: Boolean
): ConfiguredRecordFormat {
    companion object {
        private const val reason = "Whole file is the selected format, so the file is passed on without being read"
    }


    override val automaticDetectionCandidate: Boolean = false
    override val readsContent: Boolean = false
    override val hintMetadata: List<FormatHintMetadata> = emptyList()


    @Suppress("OVERRIDE_DEPRECATION")
    override fun resolvedRead(ref: DataRef): ResolvedReadSpec = UndetectedFormat.unread(reason)


    override fun declaredShape(): DataShape? = null


    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(title)
        sink.addUtf8(reason)
    }
}
