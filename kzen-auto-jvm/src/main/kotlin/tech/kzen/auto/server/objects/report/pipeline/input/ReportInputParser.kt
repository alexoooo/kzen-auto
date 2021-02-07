package tech.kzen.auto.server.objects.report.pipeline.input

import tech.kzen.auto.server.objects.report.pipeline.event.handoff.RecordHandoff
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordParser
import tech.kzen.auto.server.objects.report.pipeline.progress.ReportProgressTracker


class ReportInputParser(
    private val firstRowHeader: Boolean = true,
    private val progress: ReportProgressTracker?
) {
    //-----------------------------------------------------------------------------------------------------------------
    private var currentInputKey: String? = null
    private var currentParser: RecordParser? = null
    private var previousRecordHeader: RecordHeader = RecordHeader.empty
    private val leftoverRecordLineBuffer = RecordItemBuffer()
    private var currentProgress: ReportProgressTracker.Buffer? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun parse(data: RecordDataBuffer, recordHandoff: RecordHandoff) {
        val parser = getOrCreateParser(data)

        val startIndex: Int
        if (firstRowHeader && previousRecordHeader.isEmpty()) {
            val headerDone = parseHeader(data, parser)
            if (! headerDone) {
                return
            }
            startIndex = 1
        }
        else if (! leftoverRecordLineBuffer.isEmpty) {
            val partialDone = continuePartial(data, recordHandoff, parser)
            if (! partialDone) {
                return
            }
            startIndex = 1
        }
        else {
            startIndex = 0
        }

        val chars = data.chars
        val recordTokenBuffer = data.recordTokenBuffer

        val lastFullIndex =
            if (recordTokenBuffer.partialLast) {
                recordTokenBuffer.count - 2
            }
            else {
                recordTokenBuffer.count - 1
            }

        for (i in startIndex .. lastFullIndex) {
            val record = recordHandoff.next()
            record.header.value = previousRecordHeader

            val recordItem = record.item
            recordItem.clear()

            parser.parseFull(
                recordItem,
                chars,
                recordTokenBuffer.offset(i),
                recordTokenBuffer.length(i),
                recordTokenBuffer.fieldCount(i))

            recordHandoff.commit()
        }

        val fullParsedCount = lastFullIndex - startIndex + 1
        currentProgress?.nextParsed(fullParsedCount)

        if (recordTokenBuffer.partialLast) {
            val lastIndex = recordTokenBuffer.count - 1
            parser.parsePartial(
                leftoverRecordLineBuffer,
                chars,
                recordTokenBuffer.offset(lastIndex),
                recordTokenBuffer.length(lastIndex),
                recordTokenBuffer.fieldCount(lastIndex),
                true)
        }

        if (data.endOfStream) {
            onEndOfStream()
        }
    }


    private fun parseHeader(data: RecordDataBuffer, parser: RecordParser): Boolean {
        if (data.recordTokenBuffer.hasFull()) {
            parser.parseFull(
                leftoverRecordLineBuffer,
                data.chars,
                data.recordTokenBuffer.offset(0),
                data.recordTokenBuffer.length(0),
                data.recordTokenBuffer.fieldCount(0))

            previousRecordHeader = RecordHeader.ofLine(leftoverRecordLineBuffer)
            leftoverRecordLineBuffer.clear()

            return true
        }
        else {
            parser.parsePartial(
                leftoverRecordLineBuffer,
                data.chars,
                data.recordTokenBuffer.offset(0),
                data.recordTokenBuffer.length(0),
                data.recordTokenBuffer.fieldCount(0),
                true)

            return false
        }
    }


    private fun continuePartial(
        data: RecordDataBuffer,
        recordHandoff: RecordHandoff,
        parser: RecordParser
    ): Boolean {
        if (data.recordTokenBuffer.hasFull()) {
            parser.parsePartial(
                leftoverRecordLineBuffer,
                data.chars,
                data.recordTokenBuffer.offset(0),
                data.recordTokenBuffer.length(0),
                data.recordTokenBuffer.fieldCount(0),
                false)

            val record = recordHandoff.next()
            record.header.value = previousRecordHeader
            record.item.copy(leftoverRecordLineBuffer)
            recordHandoff.commit()

            leftoverRecordLineBuffer.clear()

            return true
        }
        else {
            parser.parsePartial(
                leftoverRecordLineBuffer,
                data.chars,
                data.recordTokenBuffer.offset(0),
                data.recordTokenBuffer.length(0),
                data.recordTokenBuffer.fieldCount(0),
                true)

            return false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun getOrCreateParser(data: RecordDataBuffer): RecordParser {
        if (currentInputKey == null) {
            check(currentParser == null)

            currentInputKey = data.inputKey!!
            currentParser = RecordParser.forExtension(data.innerExtension!!)
            currentProgress = progress?.getRunning(currentInputKey!!)
        }
        return currentParser!!
    }


    private fun onEndOfStream() {
        previousRecordHeader = RecordHeader.empty
        currentInputKey = null
        currentParser = null

        currentProgress?.finishParsing()
        currentProgress = null
    }
}