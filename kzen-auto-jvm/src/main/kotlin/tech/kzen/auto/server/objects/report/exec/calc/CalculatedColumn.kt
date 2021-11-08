package tech.kzen.auto.server.objects.report.exec.calc

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.plugin.model.record.FlatFileRecord


interface CalculatedColumn<T> {
    // TODO: primitive and Any return type handling for performance
    fun evaluate(
        model: T,
        flatFileRecord: FlatFileRecord,
        headerListing: HeaderListing
    ): ColumnValue
}