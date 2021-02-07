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
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
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


        fun head(file: Path, numberOfRecords: Int = 1): List<RecordItemBuffer> {
            val instance = ReportInputChain(FileFlatData(file))
            val records = mutableListOf<RecordItemBuffer>()

            while (true) {
                val hasNext = instance.poll {
                    records.add(it.prototype())
                }
                if (! hasNext || records.size >= numberOfRecords) {
                    break
                }
            }

            return when {
                records.size <= numberOfRecords ->
                    records

                else ->
                    records.subList(0, numberOfRecords)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val reader = ReportInputReader.single(flatData)
    private val decoder = ReportInputDecoder()
    private val lexer = ReportInputLexer()
    private val parser = ReportInputParser(false, null)

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

        recordHandoff.flush { visitor.invoke(it.item) }

        return remaining
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        reader.close()
    }
}