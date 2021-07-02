package tech.kzen.auto.server.objects.report.pipeline.output.export.format

import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.plugin.model.record.FlatFileRecord


class TsvExportFormatter:
    RecordFormat
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // maxOf('\t', '\r', '\n')
        private const val maxSpecial: Char = 13.toChar()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun format(record: FlatFileRecord, output: DataRecordBuffer) {
        val fieldContents = record.fieldContentsUnsafe()
        val fieldEnds = record.fieldEndsUnsafe()
        val fieldCount = record.fieldCount()
        val fieldContentLength = fieldEnds[fieldCount - 1]

        val outputLength = fieldContentLength + fieldCount
        output.ensureCharCapacity(output.charsLength + outputLength)

        val outputChars = output.chars
        var nextOutput = output.charsLength
        var nextStart = 0

        for (i in 0 until fieldCount) {
            val end = fieldEnds[i]

            for (j in nextStart until end) {
                val nextChar = fieldContents[j]

                if (nextChar > maxSpecial) {
                    outputChars[nextOutput++] = nextChar
                }
                else {
                    when (nextChar) {
                        '\t' ->
                            throw IllegalArgumentException("Tab character (\\t) cannot be exported to TSV")

                        '\r' ->
                            throw IllegalArgumentException("Carriage return (\\r) cannot be exported to TSV")

                        '\n' ->
                            throw IllegalArgumentException("Line feed (\\n) cannot be exported to TSV")

                        else ->
                            outputChars[nextOutput++] = nextChar
                    }
                }
            }

            outputChars[nextOutput++] = '\t'
            nextStart = end
        }

        outputChars[nextOutput - 1] = '\n'
        output.charsLength = nextOutput
    }
}