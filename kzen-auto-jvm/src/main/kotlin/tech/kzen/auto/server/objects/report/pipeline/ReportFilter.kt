package tech.kzen.auto.server.objects.report.pipeline

import tech.kzen.auto.common.objects.document.report.spec.ColumnFilterType
import tech.kzen.auto.server.objects.report.model.RecordItem
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import java.util.function.Predicate


class ReportFilter(
    reportRunSpec: ReportRunSpec
):
    Predicate<RecordItem>
{
    private val filterColumnNames = reportRunSpec.columnNames
        .intersect(reportRunSpec.filter.columns.keys)
        .toList()

    private val columnFilterSpecs = reportRunSpec.filter.columns


    override fun test(item: RecordItem): Boolean {
        for (filterColumn in filterColumnNames) {
            val value = item.get(filterColumn)

            @Suppress("MapGetWithNotNullAssertionOperator")
            val columnCriteria = columnFilterSpecs[filterColumn]!!

            if (columnCriteria.values.isNotEmpty()) {
                val present = columnCriteria.values.contains(value)

                val allow =
                    when (columnCriteria.type) {
                        ColumnFilterType.RequireAny ->
                            present

                        ColumnFilterType.ExcludeAll ->
                            ! present
                    }

                if (! allow) {
                    return false
                }
            }
        }

        return true
    }
}