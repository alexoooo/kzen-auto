package tech.kzen.auto.server.objects.report.exec.stages

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.filter.ColumnFilterType
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatFileRecordField
import tech.kzen.auto.server.objects.report.exec.ReportProcessorStage
import tech.kzen.auto.server.objects.report.exec.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.exec.input.model.header.RecordHeader
import tech.kzen.auto.server.objects.report.exec.input.model.header.RecordHeaderIndex
import tech.kzen.auto.server.objects.report.model.ReportRunContext


class ProcessorFilterStage(
    reportRunContext: ReportRunContext
):
    ReportProcessorStage<ProcessorOutputEvent<*>>("filter")
{
    //-----------------------------------------------------------------------------------------------------------------
    private val filterColumnNames = reportRunContext
        .inputAndFormulaColumns
        .values
        .intersect(reportRunContext.filter.columns.keys)
        .toList()

    private val nonEmptyFilterColumnNames = filterColumnNames
        .filter { columnName ->
            val spec = reportRunContext.filter.columns[columnName]
                ?: error("Missing: $columnName")

            spec.values.isNotEmpty()
        }


    private val recordHeaderIndex = RecordHeaderIndex(
        HeaderListing(nonEmptyFilterColumnNames))

    private val columnFilterSpecTypes: List<ColumnFilterType>
    private val columnFilterSpecValues: List<Set<FlatFileRecordField>>

    init {
        val columnFilterSpecs = nonEmptyFilterColumnNames
            .map { reportRunContext.filter.columns.getValue(it) }

        columnFilterSpecTypes = columnFilterSpecs.map { it.type }

        columnFilterSpecValues = columnFilterSpecs
            .map { columnFilterSpec ->
                columnFilterSpec
                    .values
                    .map { FlatFileRecordField.standalone(it) }
                    .toSet()
            }
    }

    private val flyweight =
        FlatFileRecordField()


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return nonEmptyFilterColumnNames.isEmpty()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ProcessorOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
//        if (++count == 794477L) {
//            println("> $count");
//        }

        if (event.skip) {
            return
        }

        event.skip = ! test(event.row, event.header.value)
    }


    private fun test(row: FlatFileRecord, header: RecordHeader): Boolean {
        val columnIndices = recordHeaderIndex.indices(header)

        flyweight.selectHost(row)

        for (i in nonEmptyFilterColumnNames.indices) {
            val columnCriteriaType = columnFilterSpecTypes[i]
            val columnCriteriaSpecValues = columnFilterSpecValues[i]

            val indexInRow = columnIndices[i]
            if (indexInRow == -1) {
                if (columnCriteriaType == ColumnFilterType.RequireAny) {
                    return false
                }
            }
            else {
                flyweight.selectField(indexInRow)
                val present = columnCriteriaSpecValues.contains(flyweight)

                val reject = columnCriteriaType.reject(present)
                if (reject) {
                    return false
                }
            }

//            if (i > 0) {
//                println("foo - $count")
//            }
        }

        return true
    }
}