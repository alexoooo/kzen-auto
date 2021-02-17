package tech.kzen.auto.server.objects.report.pipeline.input.util

import tech.kzen.auto.server.objects.report.pipeline.event.handoff.ListRecordHandoff
import tech.kzen.auto.server.objects.report.pipeline.input.ReportInputDecoder
import tech.kzen.auto.server.objects.report.pipeline.input.ReportInputLexer
import tech.kzen.auto.server.objects.report.pipeline.input.ReportInputParser
import tech.kzen.auto.server.objects.report.pipeline.input.ReportInputReader
import tech.kzen.auto.server.objects.report.pipeline.input.connect.FileFlatData
import tech.kzen.auto.server.objects.report.pipeline.input.connect.FlatData
import tech.kzen.auto.server.objects.report.pipeline.input.connect.LiteralFlatData
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordFormat
import java.nio.file.Path


class ReportInputChain(
    flatData: FlatData,
    bufferSize: Int = RecordDataBuffer.defaultBufferSize
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun all(flatData: FlatData, bufferSize: Int): List<RecordItemBuffer> {
            val instance = ReportInputChain(flatData, bufferSize)
            val records = mutableListOf<RecordItemBuffer>()

            while (true) {
                val hasNext = instance.poll {
                    records.add(it.prototype())
                }
                if (! hasNext) {
                    break
                }
            }

            return records
        }


        fun allCsv(text: String, bufferSize: Int = RecordDataBuffer.defaultBufferSize): List<RecordItemBuffer> {
            return all(LiteralFlatData.ofCsv(text), bufferSize)
        }


        fun allTsv(text: String, bufferSize: Int = RecordDataBuffer.defaultBufferSize): List<RecordItemBuffer> {
            return all(LiteralFlatData.ofTsv(text), bufferSize)
        }


        fun allText(text: String, bufferSize: Int = RecordDataBuffer.defaultBufferSize): List<RecordItemBuffer> {
            return all(LiteralFlatData.ofText(text), bufferSize)
        }


        fun header(file: Path): RecordHeader {
            val instance = ReportInputChain(FileFlatData(file))
            var header: RecordHeader? = null

            while (true) {
                val hasNext = instance.pollHeader {
                    header = it
                }
                if (! hasNext || header != null) {
                    break
                }
            }

            return header ?: RecordHeader.empty
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val reader = ReportInputReader.single(flatData)
    private val decoder = ReportInputDecoder()
    private val lexer = ReportInputLexer()

    private val parser = ReportInputParser(
        null,
        RecordFormat
            .forExtension(flatData.innerExtension())
            .withDefaultHeader(RecordHeader.empty)
    )

    private val dataBuffer = RecordDataBuffer.ofBufferSize(bufferSize)
    private val recordHandoff = ListRecordHandoff()


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return true if has next
     */
    fun poll(visitor: (RecordItemBuffer) -> Unit): Boolean {
        val remaining = reader.poll(dataBuffer)

        decoder.decode(dataBuffer)
        lexer.tokenize(dataBuffer)
        parser.parse(dataBuffer, recordHandoff)

        recordHandoff.flush { recordMap ->
//            recordMap.item.populateCaches()
            visitor.invoke(recordMap.item)
        }

        return remaining
    }


    fun pollHeader(visitor: (RecordHeader) -> Unit): Boolean {
        val remaining = reader.poll(dataBuffer)

        decoder.decode(dataBuffer)
        lexer.tokenize(dataBuffer)
        parser.parse(dataBuffer, recordHandoff)

        recordHandoff.flush { recordMap ->
            visitor.invoke(recordMap.header.value)
        }

        return remaining
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        reader.close()
    }
}