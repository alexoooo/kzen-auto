package tech.kzen.auto.server.objects.report.exec.output.export

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.server.objects.report.exec.ReportProcessorStage
import tech.kzen.auto.server.objects.report.exec.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.exec.input.model.header.RecordHeaderIndex


// TODO: use bulk copy instead of copying field-by-field, or maybe even add special RowView
class ExportColumnNormalizer(
    private val filteredColumns: HeaderListing
):
    ReportProcessorStage<ProcessorOutputEvent<*>>("export-normalize")
{
    //-----------------------------------------------------------------------------------------------------------------
    private val recordHeaderIndex = RecordHeaderIndex(filteredColumns)
    private val normalizedColumnCount = filteredColumns.values.size


    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ProcessorOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (event.skip) {
            return
        }

        val row = event.row
        val rowHeader = event.header.value.headerNames
        check(row.fieldCount() == rowHeader.values.size) {
            "Mismatch between header column could and row column count " +
                    "(${rowHeader.values.size} vs ${row.fieldCount()}): ${rowHeader.values} vs $row"
        }

        val normalizedRow = event.normalizedRow

        if (filteredColumns == rowHeader) {
            normalizedRow.clone(row)
            return
        }

        normalizedRow.clear()
        normalizedRow.growTo(row.fieldContentLength(), normalizedColumnCount)

        val columnIndices = recordHeaderIndex.indices(rowHeader)
        val fieldEnds = row.fieldEndsUnsafe()
        val fieldContents = row.fieldContentsUnsafe()

        for (i in 0 until normalizedColumnCount) {
            val indexInRow = columnIndices[i]
            if (indexInRow == -1) {
                normalizedRow.commitFieldUnsafe()
            }
            else {
                val startIndex = if (indexInRow == 0) 0 else fieldEnds[indexInRow - 1]
                val valueLength = fieldEnds[indexInRow] - startIndex
                normalizedRow.addToFieldAndCommitUnsafe(fieldContents, startIndex, valueLength)
            }
        }
    }
}