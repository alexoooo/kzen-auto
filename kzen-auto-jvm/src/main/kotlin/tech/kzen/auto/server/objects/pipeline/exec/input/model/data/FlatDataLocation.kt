package tech.kzen.auto.server.objects.pipeline.exec.input.model.data

import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.plugin.spec.DataEncodingSpec
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class FlatDataLocation(
    val dataLocation: DataLocation,
    val dataEncoding: DataEncodingSpec
): Digestible {
    companion object {
        val literalUtf8 = FlatDataLocation(DataLocation.unknown, DataEncodingSpec.utf8)
    }


    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(dataLocation)
        sink.addUtf8Nullable(dataEncoding.textEncoding?.charset?.displayName())
    }
}