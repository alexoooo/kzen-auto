package tech.kzen.auto.server.objects.report.exec.input.model.header

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing


data class RecordHeaderBuffer(
//    var value: RecordHeader = RecordHeader.empty
    var value: HeaderListing = HeaderListing.empty
)