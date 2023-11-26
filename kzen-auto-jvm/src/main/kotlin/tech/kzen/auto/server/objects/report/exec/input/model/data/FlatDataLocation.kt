package tech.kzen.auto.server.objects.report.exec.input.model.data

import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.plugin.spec.DataEncodingSpec
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


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