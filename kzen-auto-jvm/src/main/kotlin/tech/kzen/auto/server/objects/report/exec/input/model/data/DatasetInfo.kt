package tech.kzen.auto.server.objects.report.exec.input.model.data

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class DatasetInfo(
    val items: List<FlatDataInfo>
): Digestible {
    fun headerSuperset(): HeaderListing {
        return HeaderListing(items.flatMap { it.headerListing.values }.toSet().toList())
    }


//    fun dataLocations(): List<DataLocation> {
//        return items.map { it.flatDataLocation.dataLocation }
//    }


    override fun digest(sink: Digest.Sink) {
        sink.addDigestibleUnorderedList(items)
    }
}