package tech.kzen.auto.server.objects.report.exec.input.model.header

import tech.kzen.auto.common.data.schema.HeaderListing


data class RecordHeaderBuffer(
//    var value: RecordHeader = RecordHeader.empty
    var value: HeaderListing = HeaderListing.empty
)