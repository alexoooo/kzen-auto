package tech.kzen.auto.server.objects.report.pipeline.input.v2.model

import tech.kzen.auto.common.objects.document.report.listing.DataLocation
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class DatasetInfo(
    val items: List<FlatDataInfo>
): Digestible {
    fun headerSuperset(): HeaderListing {
        return HeaderListing(items.flatMap { it.headerListing.values }.toSet().toList())
    }


    fun dataLocations(): List<DataLocation> {
        return items.map { it.dataLocationInfo.dataLocation }
    }


    override fun digest(builder: Digest.Builder) {
        builder.addDigestibleUnorderedList(items)
    }
}