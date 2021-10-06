package tech.kzen.auto.server.objects.report.exec.output.export.format

import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.plugin.model.record.FlatFileRecord


class CsvExportFormatter:
    RecordFormat
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // maxOf('"', ',', '\r', '\n')
        private const val maxSpecial: Char = 44.toChar()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun format(record: FlatFileRecord, output: DataRecordBuffer) {
        val fieldContents = record.fieldContentsUnsafe()
        val fieldEnds = record.fieldEndsUnsafe()
        val fieldCount = record.fieldCount()
        val fieldContentLength = fieldEnds[fieldCount - 1]

        val outputLength = fieldContentLength * 2 + fieldCount * 3
        output.ensureCharCapacity(output.charsLength + outputLength)

        val outputChars = output.chars
        var nextOutput = output.charsLength
        var nextStart = 0

        for (i in 0 until fieldCount) {
            val end = fieldEnds[i]

            var hasSpecial = false
            for (j in nextStart until end) {
                val nextChar = fieldContents[j]
                if (nextChar < maxSpecial) {
                    when (nextChar) {
                        ',', '"', '\r', '\n' -> {
                            hasSpecial = true
                            break
                        }
                    }
                }
            }

            if (hasSpecial) {
                outputChars[nextOutput++] = '"'
                for (j in nextStart until end) {
                    val nextChar = fieldContents[j]

                    if (nextChar == '"') {
                        outputChars[nextOutput++] = '"'
                        outputChars[nextOutput++] = '"'
                    }
                    else {
                        outputChars[nextOutput++] = nextChar
                    }
                }
                outputChars[nextOutput++] = '"'
            }
            else {
                val length = end - nextStart
                System.arraycopy(fieldContents, nextStart, outputChars, nextOutput, length)
                nextOutput += length
            }

            outputChars[nextOutput++] = ','
            nextStart = end
        }

        outputChars[nextOutput - 1] = '\n'
        output.charsLength = nextOutput
    }
}