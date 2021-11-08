package tech.kzen.auto.server.objects.report.exec.output.export.format

import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.plugin.model.record.FlatFileRecord


class CsvExportFormatter:
    RecordFormat
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // maxOf(',', '"', '\r', '\n')
        private const val maxSpecial: Char = 44.toChar()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun format(record: FlatFileRecord, output: DataRecordBuffer) {
        val fieldContents = record.fieldContentsUnsafe()
        val fieldEnds = record.fieldEndsUnsafe()
        val fieldCount = record.fieldCount()
        val fieldContentLength = fieldEnds[fieldCount - 1]

        // if it's all quotes (i.e. """""""","""",...) + first empty field in empty record
        val maxOutputLength = fieldContentLength * 2 + fieldCount * 3 + 2
        output.ensureCharCapacity(output.charsLength + maxOutputLength)

        val outputChars = output.chars
        var nextOutput = output.charsLength
        var nextStart = 0

        for (i in 0 until fieldCount) {
            val end = fieldEnds[i]

            // NB: behaviour align with commons-csv
            //  https://github.com/apache/commons-csv/blob/c797b6109ec108d357cf254191aa232dd0c03710/src/main/java/org/apache/commons/csv/CSVFormat.java#L2000
            var hasSpecial = false
            if (nextStart == end) {
                hasSpecial = i == 0
            }
            else if (fieldContents[nextStart] == '#') {
                hasSpecial = true
            }
            else {
                specialCharLoop@
                for (j in nextStart until end) {
                    val nextChar = fieldContents[j]
                    if (nextChar <= maxSpecial) {
                        when (nextChar) {
                            ',', '"', '\r', '\n' -> {
                                hasSpecial = true
                                break@specialCharLoop
                            }
                        }
                    }
                }
                if (! hasSpecial) {
                    hasSpecial = fieldContents[end - 1] <= ' '
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