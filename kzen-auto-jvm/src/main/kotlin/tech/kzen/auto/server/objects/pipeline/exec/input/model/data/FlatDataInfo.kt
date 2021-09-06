package tech.kzen.auto.server.objects.pipeline.exec.input.model.data

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.util.data.DataLocationGroup
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.server.objects.plugin.PluginUtils
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class FlatDataInfo(
    val flatDataLocation: FlatDataLocation,
    val headerListing: HeaderListing,
    val processorPluginCoordinate: PluginCoordinate,
    val group: DataLocationGroup
):
    Digestible,
    Comparable<FlatDataInfo>
{
    override fun digest(builder: Digest.Builder) {
        builder.addDigestible(flatDataLocation)
        builder.addDigestible(headerListing)
        PluginUtils.digestPluginCoordinate(processorPluginCoordinate, builder)
        builder.addUtf8Nullable(group.group)
    }


    override fun compareTo(other: FlatDataInfo): Int {
        val groupCmp = group.compareTo(other.group)
        if (groupCmp != 0) {
            return groupCmp
        }

        return flatDataLocation.dataLocation.asString().compareTo(other.flatDataLocation.toString())
    }
}