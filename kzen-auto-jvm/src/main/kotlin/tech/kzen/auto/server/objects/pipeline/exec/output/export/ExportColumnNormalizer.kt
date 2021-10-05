package tech.kzen.auto.server.objects.pipeline.exec.output.export

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.server.objects.pipeline.exec.PipelineProcessorStage
import tech.kzen.auto.server.objects.pipeline.exec.input.model.header.RecordHeaderIndex
import tech.kzen.auto.server.objects.pipeline.exec.event.ProcessorOutputEvent


class ExportColumnNormalizer(
    private val filteredColumns: HeaderListing
):
    PipelineProcessorStage<ProcessorOutputEvent<*>>("export-normalize")
{
    //-----------------------------------------------------------------------------------------------------------------
    private val recordHeaderIndex = RecordHeaderIndex(filteredColumns)
    private val normalizedColumnCount = filteredColumns.values.size

//    private var previousGroup: DataLocationGroup? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ProcessorOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (event.skip) {
            return
        }

        val row = event.row
        val normalizedRow = event.normalizedRow

        if (filteredColumns == event.header.value.headerNames) {
            normalizedRow.clone(row)
            return
        }

        normalizedRow.clear()
        normalizedRow.growTo(row.fieldContentLength(), normalizedColumnCount)

        val columnIndices = recordHeaderIndex.indices(event.header.value)
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