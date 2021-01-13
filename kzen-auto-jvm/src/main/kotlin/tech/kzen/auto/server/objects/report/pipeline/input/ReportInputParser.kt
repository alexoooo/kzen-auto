package tech.kzen.auto.server.objects.report.pipeline.input

import tech.kzen.auto.server.objects.report.pipeline.event.handoff.RecordHandoff
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordParser


class ReportInputParser(
    private val firstRowHeader: Boolean = true
) {
    //-----------------------------------------------------------------------------------------------------------------
    private var location: String? = null
    private var lexerParser: RecordParser? = null
    private var previousRecordHeader: RecordHeader = RecordHeader.empty
    private val leftoverRecordLineBuffer = RecordItemBuffer()


    //-----------------------------------------------------------------------------------------------------------------
    fun parse(data: RecordDataBuffer, recordHandoff: RecordHandoff) {
//        check(data.recordTokenBuffer.count != 0) {
//            "foo"
//        }

        if (location == null) {
            location = data.location!!
            lexerParser = RecordParser.forExtension(data.innerExtension!!)
//            lexerParser = TsvLexerParser()
        }

        val startIndex: Int
        if (firstRowHeader && previousRecordHeader.isEmpty()) {
            val headerDone = parseHeader(data)
            if (! headerDone) {
                return
            }
            startIndex = 1
        }
        else if (! leftoverRecordLineBuffer.isEmpty) {
            val partialDone = continuePartial(data, recordHandoff)
            if (! partialDone) {
                return
            }
            startIndex = 1
        }
        else {
            startIndex = 0
        }

        val lastFullIndex =
            if (data.recordTokenBuffer.partialLast) {
                data.recordTokenBuffer.count - 2
            }
            else {
                data.recordTokenBuffer.count - 1
            }

        for (i in startIndex .. lastFullIndex) {
            val record = recordHandoff.next()
            record.header.value = previousRecordHeader
            record.item.clear()

            lexerParser!!.parseFull(
                record.item,
                data.chars,
                data.recordTokenBuffer.offset(i),
                data.recordTokenBuffer.length(i),
                data.recordTokenBuffer.fieldCount(i))

            recordHandoff.commit()
        }

        if (data.recordTokenBuffer.partialLast) {
            val lastIndex = data.recordTokenBuffer.count - 1
            lexerParser!!.parsePartial(
                leftoverRecordLineBuffer,
                data.chars,
                data.recordTokenBuffer.offset(lastIndex),
                data.recordTokenBuffer.length(lastIndex),
                data.recordTokenBuffer.fieldCount(lastIndex),
                true)
        }

        if (data.endOfStream) {
            previousRecordHeader = RecordHeader.empty
            location = null
            lexerParser = null
        }
    }


    private fun parseHeader(data: RecordDataBuffer): Boolean {
        if (data.recordTokenBuffer.hasFull()) {
            lexerParser!!.parseFull(
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
            lexerParser!!.parsePartial(
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
        recordHandoff: RecordHandoff
    ): Boolean {
        if (data.recordTokenBuffer.hasFull()) {
            lexerParser!!.parsePartial(
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
            lexerParser!!.parsePartial(
                leftoverRecordLineBuffer,
                data.chars,
                data.recordTokenBuffer.offset(0),
                data.recordTokenBuffer.length(0),
                data.recordTokenBuffer.fieldCount(0),
                true)

            return false
        }
    }
}