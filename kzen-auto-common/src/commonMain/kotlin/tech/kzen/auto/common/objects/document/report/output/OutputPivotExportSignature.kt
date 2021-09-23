package tech.kzen.auto.common.objects.document.report.output

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueTableSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueType


data class OutputPivotExportSignature(
    val header: HeaderListing,
    val valueTypes: List<IndexedValue<PivotValueType>>
) {
    companion object {
        fun of(pivotSpec: PivotSpec): OutputPivotExportSignature {
            return of(pivotSpec.rows, pivotSpec.values)
        }


        fun of(rowColumns: HeaderListing, values: PivotValueTableSpec): OutputPivotExportSignature {
            val header = mutableListOf<String>()
            val valueTypes = mutableListOf<IndexedValue<PivotValueType>>()

            for (rowColumn in rowColumns.values) {
                header.add(rowColumn)
            }

            for ((index, e) in values.columns.toList().withIndex()) {
                val valueColumn = e.first
                val valueValueSpec = e.second
                for (valueType in valueValueSpec.types) {
                    val valueHeader = "$valueColumn - $valueType"
                    header.add(valueHeader)
                    valueTypes.add(IndexedValue(index, valueType))
                }
            }

            return OutputPivotExportSignature(HeaderListing(header), valueTypes)
        }
    }
}