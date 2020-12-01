package tech.kzen.auto.server.objects.report.pipeline

import tech.kzen.auto.common.objects.document.report.spec.ColumnFilterType
import tech.kzen.auto.server.objects.report.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.input.model.RecordHeaderIndex
import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer
import tech.kzen.auto.server.objects.report.input.model.RecordTextFlyweight
import tech.kzen.auto.server.objects.report.model.ReportRunSpec


class ReportFilter(
    reportRunSpec: ReportRunSpec
) {
    //-----------------------------------------------------------------------------------------------------------------
    private val filterColumnNames = reportRunSpec.columnNames
        .intersect(reportRunSpec.filter.columns.keys)
        .toList()

    private val nonEmptyFilterColumnNames = filterColumnNames
        .filter { columnName ->
            val spec = reportRunSpec.filter.columns[columnName]
                ?: error("Missing: $columnName")

            spec.values.isNotEmpty()
        }


    private val recordHeaderIndex = RecordHeaderIndex(nonEmptyFilterColumnNames)

    private val columnFilterSpecTypes: List<ColumnFilterType>
    private val columnFilterSpecValues: List<Set<RecordTextFlyweight>>

    init {
        val columnFilterSpecs = nonEmptyFilterColumnNames
            .map { reportRunSpec.filter.columns.getValue(it) }

        columnFilterSpecTypes = columnFilterSpecs.map { it.type }

        columnFilterSpecValues = columnFilterSpecs
            .map { columnFilterSpec ->
                columnFilterSpec
                    .values
                    .map { RecordTextFlyweight.standalone(it) }
                    .toSet()
            }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun test(item: RecordLineBuffer, header: RecordHeader): Boolean {
        val itemIndices = recordHeaderIndex.indices(header)

        for (i in nonEmptyFilterColumnNames.indices) {
            val columnCriteriaType = columnFilterSpecTypes[i]
            val columnCriteriaSpecValues = columnFilterSpecValues[i]

            val indexInItem = itemIndices[i]
            if (indexInItem == -1) {
                if (columnCriteriaType == ColumnFilterType.RequireAny) {
                    return false
                }
            }
            else {
                item.selectFlyweight(indexInItem)
                val present = columnCriteriaSpecValues.contains(item.flyweight)

                when (columnCriteriaType) {
                    ColumnFilterType.RequireAny ->
                        if (! present) {
                            return false
                        }

                    ColumnFilterType.ExcludeAll ->
                        if (present) {
                            return false
                        }
                }
            }
        }

        return true
    }
}