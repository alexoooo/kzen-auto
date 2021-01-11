package tech.kzen.auto.server.objects.report.pipeline.input

import com.lmax.disruptor.RingBuffer
import tech.kzen.auto.server.objects.report.pipeline.ReportPipeline
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordLexerParser
import java.nio.file.Path


class ReportInputParser {
    //-----------------------------------------------------------------------------------------------------------------
    private var location: Path? = null
    private var lexerParser: RecordLexerParser? = null
    private var previousRecordHeader: RecordHeader = RecordHeader.empty
    private val leftoverRecordLineBuffer =
        RecordItemBuffer()


    //-----------------------------------------------------------------------------------------------------------------
    fun parse(data: RecordDataBuffer, recordRingBuffer: RingBuffer<ReportPipeline.RecordEvent>) {
//        check(data.recordTokenBuffer.count != 0) {
//            "foo"
//        }

        if (location == null) {
            location = data.location!!
            lexerParser = RecordLexerParser.forExtension(data.innerExtension!!)
//            lexerParser = TsvLexerParser()
        }

        val startIndex: Int
        if (previousRecordHeader.isEmpty()) {
            val headerDone = parseHeader(data)
            if (! headerDone) {
                return
            }
            startIndex = 1
        }
        else if (! leftoverRecordLineBuffer.isEmpty()) {
            val partialDone = continuePartial(data, recordRingBuffer)
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
            val sequence = recordRingBuffer.next()
            val event = recordRingBuffer.get(sequence)
            val record = event.record
            record.header.value = previousRecordHeader
            record.item.clear()

            lexerParser!!.parseFull(
                record.item,
                data.chars,
                data.recordTokenBuffer.offset(i),
                data.recordTokenBuffer.length(i),
                data.recordTokenBuffer.fieldCount(i))

            recordRingBuffer.publish(sequence)
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
        recordRingBuffer: RingBuffer<ReportPipeline.RecordEvent>
    ): Boolean {
        if (data.recordTokenBuffer.hasFull()) {
            lexerParser!!.parsePartial(
                leftoverRecordLineBuffer,
                data.chars,
                data.recordTokenBuffer.offset(0),
                data.recordTokenBuffer.length(0),
                data.recordTokenBuffer.fieldCount(0),
                false)

            val sequence = recordRingBuffer.next()
            val event = recordRingBuffer.get(sequence)
            val record = event.record
            record.header.value = previousRecordHeader
            record.item.copy(leftoverRecordLineBuffer)
            recordRingBuffer.publish(sequence)

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