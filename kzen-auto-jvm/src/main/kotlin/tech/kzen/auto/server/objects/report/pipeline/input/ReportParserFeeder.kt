package tech.kzen.auto.server.objects.report.pipeline.input

import com.lmax.disruptor.RingBuffer
import tech.kzen.auto.server.objects.report.pipeline.ReportPipeline
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordParser
import java.nio.file.Path


class ReportParserFeeder {
    //-----------------------------------------------------------------------------------------------------------------
    private var location: Path? = null
    private var parser: RecordParser? = null
    private var previousRecordHeader: RecordHeader = RecordHeader.empty
    private val leftoverRecordLineBuffer =
        RecordItemBuffer()


    //-----------------------------------------------------------------------------------------------------------------
    fun parse(data: RecordDataBuffer, recordRingBuffer: RingBuffer<ReportPipeline.RecordEvent>) {
        if (location == null) {
            location = data.location!!
            parser = RecordParser.forExtension(data.innerExtension!!)
        }

        var offset = 0
        if (previousRecordHeader.isEmpty()) {
            val recordLength = parser!!.parseNext(
                leftoverRecordLineBuffer, data.chars, offset, data.charsLength)

            if (recordLength != -1) {
                previousRecordHeader = RecordHeader.ofLine(leftoverRecordLineBuffer)
                leftoverRecordLineBuffer.clear()
                offset += recordLength
            }
            else {
                offset = -1
            }
        }
        else if (! leftoverRecordLineBuffer.isEmpty()) {
            val recordLength = parser!!.parseNext(
                leftoverRecordLineBuffer, data.chars, offset, data.charsLength)

            if (recordLength != -1) {
                val sequence = recordRingBuffer.next()
                val event = recordRingBuffer.get(sequence)
                val record = event.record
                event.noop = false
                record.header.value = previousRecordHeader
                record.item.copy(leftoverRecordLineBuffer)
                recordRingBuffer.publish(sequence)

                leftoverRecordLineBuffer.clear()
                offset += recordLength
            }
            else {
                offset = -1
            }
        }

        if (offset != -1) {
            while (true) {
                val sequence = recordRingBuffer.next()
                val event = recordRingBuffer.get(sequence)
                val record = event.record
                record.clear()

                val recordLength = parser!!.parseNext(
                    record.item, data.chars, offset, data.charsLength)

                if (recordLength != -1) {
                    event.noop = false
                    record.header.value = previousRecordHeader
                    recordRingBuffer.publish(sequence)

                    offset += recordLength
                }
                else {
                    leftoverRecordLineBuffer.copy(record.item)

                    event.noop = true
                    recordRingBuffer.publish(sequence)
                    break
                }
            }
        }

        if (data.endOfStream) {
            if (! leftoverRecordLineBuffer.isEmpty()) {
                parser!!.endOfStream(leftoverRecordLineBuffer)

                val sequence = recordRingBuffer.next()
                val event = recordRingBuffer.get(sequence)
                val record = event.record
                record.clear()
                event.noop = false
                record.header.value = previousRecordHeader
                record.item.copy(leftoverRecordLineBuffer)
                recordRingBuffer.publish(sequence)

                leftoverRecordLineBuffer.clear()
            }
            previousRecordHeader = RecordHeader.empty

            location = null
            parser = null
        }
    }
}