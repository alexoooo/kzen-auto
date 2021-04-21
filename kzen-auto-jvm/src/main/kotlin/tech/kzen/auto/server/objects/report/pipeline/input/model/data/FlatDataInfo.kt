package tech.kzen.auto.server.objects.report.pipeline.input.model.data

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.server.objects.plugin.PluginUtils
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class FlatDataInfo(
    val flatDataLocation: FlatDataLocation,
    val headerListing: HeaderListing,
    val processorPluginCoordinate: PluginCoordinate
): Digestible {
    override fun digest(builder: Digest.Builder) {
        builder.addDigestible(flatDataLocation)
        builder.addDigestible(headerListing)
        PluginUtils.digestPluginCoordinate(processorPluginCoordinate, builder)
    }
}