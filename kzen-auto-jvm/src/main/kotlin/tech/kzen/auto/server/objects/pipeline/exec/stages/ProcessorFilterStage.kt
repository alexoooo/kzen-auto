package tech.kzen.auto.server.objects.pipeline.exec.stages

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.filter.ColumnFilterType
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatFileRecordField
import tech.kzen.auto.server.objects.pipeline.exec.PipelineProcessorStage
import tech.kzen.auto.server.objects.pipeline.model.ReportRunContext
import tech.kzen.auto.server.objects.report.pipeline.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.pipeline.exec.input.model.header.RecordHeader
import tech.kzen.auto.server.objects.pipeline.exec.input.model.header.RecordHeaderIndex


class ProcessorFilterStage(
    reportRunContext: ReportRunContext
):
    PipelineProcessorStage<ProcessorOutputEvent<*>>("filter")
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
        HeaderListing(nonEmptyFilterColumnNames)
    )

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
        val itemIndices = recordHeaderIndex.indices(header)

        flyweight.selectHost(row)

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
                flyweight.selectField(indexInItem)
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