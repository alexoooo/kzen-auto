package tech.kzen.auto.server.objects.report.pipeline.input.v2.model

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class FlatDataInfo(
    val dataLocationInfo: DataLocationInfo,
    val headerListing: HeaderListing
): Digestible {
    override fun digest(builder: Digest.Builder) {
        builder.addDigestible(dataLocationInfo)
        builder.addDigestible(headerListing)
    }
}