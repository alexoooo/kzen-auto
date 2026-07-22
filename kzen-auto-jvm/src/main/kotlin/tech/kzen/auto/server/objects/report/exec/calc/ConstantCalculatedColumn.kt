package tech.kzen.auto.server.objects.report.exec.calc

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.plugin.model.record.FlatFileRecord


class ConstantCalculatedColumn<T>(
    private val value: ColumnValue
): CalculatedColumn<T> {
    companion object {
        fun <T> empty() =
            ConstantCalculatedColumn<T>(ColumnValue.ofScalar(""))

        fun <T> error() =
            ConstantCalculatedColumn<T>(ColumnValue.errorValue)
    }


    override fun evaluate(
        model: T,
        flatFileRecord: FlatFileRecord,
        headerListing: HeaderListing
    ): ColumnValue {
        return value
    }


    // Only produced for an empty formula (or an error placeholder), which has no raw value.
    override fun evaluateRaw(
        model: T,
        flatFileRecord: FlatFileRecord,
        headerListing: HeaderListing
    ): Any? {
        return null
    }
}