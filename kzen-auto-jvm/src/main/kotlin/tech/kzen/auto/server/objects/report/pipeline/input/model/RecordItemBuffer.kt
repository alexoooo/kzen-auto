package tech.kzen.auto.server.objects.report.pipeline.input.model

import tech.kzen.auto.server.objects.report.pipeline.input.parse.CsvRecordParser
import tech.kzen.auto.server.objects.report.pipeline.input.parse.TsvRecordParser
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.Writer


class RecordItemBuffer(
    expectedContentLength: Int = 0,
    expectedFieldCount: Int = 0
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun toCsv(values: List<String>): String {
            return of(values).toCsv()
        }

        fun of(vararg values: String): RecordItemBuffer {
            return of(listOf(*values))
        }

        fun of(values: List<String>): RecordItemBuffer {
            val buffer = RecordItemBuffer()

            for (value in values) {
                for (i in value.indices) {
                    buffer.addToField(value[i])
                }
                buffer.commitField()
            }

            return buffer
        }

        fun of(contents: CharArray, offset: Int, length: Int): RecordItemBuffer {
            val buffer = RecordItemBuffer(length, 1)
            contents.copyInto(buffer.fieldContents, 0, offset, offset + length)
            buffer.fieldCount = 1
            buffer.fieldEnds[0] = length
            buffer.fieldContentLength = length
            buffer.nonEmpty = true
            return buffer
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    internal var fieldContents = CharArray(expectedContentLength)
    private var fieldEnds = IntArray(expectedFieldCount)
    private var fieldCount = 0
    private var fieldContentLength = 0
    private var nonEmpty = false

    val flyweight = RecordTextFlyweight(this)


    //-----------------------------------------------------------------------------------------------------------------
    fun selectFlyweight(fieldIndex: Int) {
        val startIndex: Int
        val endIndex: Int
        if (fieldIndex == 0) {
            startIndex = 0
            endIndex = fieldEnds[0]
        }
        else {
            startIndex = fieldEnds[fieldIndex - 1]
            endIndex = fieldEnds[fieldIndex]
        }

        val length = endIndex - startIndex

        flyweight.select(fieldIndex, startIndex, length)
    }


    fun size(): Int {
        return fieldCount
    }


    fun isEmpty(): Boolean {
        return ! nonEmpty && fieldCount <= 1 && fieldContentLength == 0
    }


    fun toList(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until fieldCount) {
            val item = getString(i)
            list.add(item)
        }
        return list
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCsv(): String {
        val out = ByteArrayOutputStream()
        OutputStreamWriter(out, Charsets.UTF_8).use {
            writeCsv(it)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }


    fun writeCsv(out: Writer) {
        if (fieldCount == 1 && fieldContentLength == 0 && nonEmpty) {
            out.write("\"\"")
            return
        }

        for (i in 0 until fieldCount) {
            if (i != 0) {
                out.write(CsvRecordParser.delimiter)
            }

            writeCsvField(i, out)
        }
    }


    fun writeCsvField(index: Int, out: Writer) {
        val startIndex: Int
        val endIndex: Int
        if (index == 0) {
            startIndex = 0
            endIndex = fieldEnds[0]
        }
        else {
            startIndex = fieldEnds[index - 1]
            endIndex = fieldEnds[index]
        }

        CsvRecordParser.writeCsv(fieldContents, startIndex, endIndex, out);
    }


    fun toTsv(): String {
        val out = ByteArrayOutputStream()
        OutputStreamWriter(out, Charsets.UTF_8).use {
            writeTsv(it)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }


    private fun writeTsv(out: Writer) {
        if (fieldCount == 1 && fieldContentLength == 0 && nonEmpty) {
            throw IllegalStateException("Can't represent non-empty record with single empty column")
        }

        for (i in 0 until fieldCount) {
            if (i != 0) {
                out.write(TsvRecordParser.delimiterInt)
            }

            writeTsvField(i, out)
        }
    }


    private fun writeTsvField(index: Int, out: Writer) {
        val startIndex: Int
        val endIndex: Int
        if (index == 0) {
            startIndex = 0
            endIndex = fieldEnds[0]
        }
        else {
            startIndex = fieldEnds[index - 1]
            endIndex = fieldEnds[index]
        }
        val length = endIndex - startIndex
        out.write(fieldContents, startIndex, length)
    }


    fun getString(index: Int): String {
        val startIndex: Int
        val endIndex: Int
        if (index == 0) {
            startIndex = 0
            endIndex = fieldEnds[0]
        }
        else {
            startIndex = fieldEnds[index - 1]
            endIndex = fieldEnds[index]
        }
        val length = endIndex - startIndex

        return String(fieldContents, startIndex, length)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun indicateNonEmpty() {
        nonEmpty = true
    }


    fun addToField(nextChar: Char) {
        growFieldContentsIfRequired()
        fieldContents[fieldContentLength] = nextChar
        fieldContentLength++
    }


    fun addToField(chars: CharArray, offset: Int, length: Int) {
        if (length != 0) {
            growFieldContentsIfRequired(fieldContentLength + length)
            System.arraycopy(chars, offset, fieldContents, fieldContentLength, length)
            fieldContentLength += length
        }
    }


    fun commitField() {
        growFieldEndsIfRequired()

        fieldEnds[fieldCount] = fieldContentLength
        fieldCount++
    }


    fun addToFieldAndCommit(chars: CharArray, offset: Int, length: Int) {
        if (length != 0) {
            if (fieldContents.size < fieldContentLength + length) {
                val nextSize = (fieldContents.size * 1.2).toInt().coerceAtLeast(fieldContentLength + length)
                fieldContents = fieldContents.copyOf(nextSize)
            }
            System.arraycopy(chars, offset, fieldContents, fieldContentLength, length)
            fieldContentLength += length
        }

        if (fieldEnds.size <= fieldCount) {
            val nextSize = (fieldEnds.size * 1.2 + 1).toInt().coerceAtLeast(fieldCount)
            fieldEnds = fieldEnds.copyOf(nextSize)
        }
        fieldEnds[fieldCount] = fieldContentLength
        fieldCount++
    }


    fun add(value: String) {
        for (i in value.indices) {
            addToField(value[i])
        }
        commitField()
    }


    fun addAll(values: List<String>) {
        for (value in values) {
            add(value)
        }
    }


    fun clear() {
        fieldCount = 0
        fieldContentLength = 0
        nonEmpty = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun addToFieldUnsafe(nextChar: Char) {
        fieldContents[fieldContentLength] = nextChar
        fieldContentLength++
    }


    fun addToFieldUnsafe(chars: CharArray, offset: Int, length: Int) {
        System.arraycopy(chars, offset, fieldContents, fieldContentLength, length)
        fieldContentLength += length
    }


    fun commitFieldUnsafe() {
        fieldEnds[fieldCount] = fieldContentLength
        fieldCount++
    }


    fun addToFieldAndCommitUnsafe(chars: CharArray, offset: Int, length: Int) {
        System.arraycopy(chars, offset, fieldContents, fieldContentLength, length)
        fieldContentLength += length

        fieldEnds[fieldCount] = fieldContentLength
        fieldCount++
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun copy(that: RecordItemBuffer) {
        fieldCount = that.fieldCount
        fieldContentLength = that.fieldContentLength
        nonEmpty = that.nonEmpty

        growFieldContentsIfRequired(fieldContentLength)
        growFieldEndsIfRequired()

        that.fieldContents.copyInto(fieldContents, endIndex = fieldContentLength)
        that.fieldEnds.copyInto(fieldEnds, endIndex = fieldCount)
    }


    fun prototype(): RecordItemBuffer {
        val prototype = RecordItemBuffer()
        prototype.copy(this)
        return prototype
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun growTo(requiredLength: Int, requiredFieldCount: Int) {
        growFieldContentsIfRequired(requiredLength)
        growFieldEndsIfRequired(requiredFieldCount)
    }


    fun growBy(additionalLength: Int, additionalFieldCount: Int) {
        growFieldContentsIfRequired(fieldContentLength + additionalLength)
        growFieldEndsIfRequired(fieldCount + additionalFieldCount)
    }


    private fun growFieldContentsIfRequired(required: Int = fieldContentLength + 1) {
        if (fieldContents.size < required) {
            val nextSize = (fieldContents.size * 1.2).toInt().coerceAtLeast(required)
            fieldContents = fieldContents.copyOf(nextSize)
        }
    }


    private fun growFieldEndsIfRequired(required: Int = fieldCount) {
        if (fieldEnds.size <= required) {
            val nextSize = (fieldEnds.size * 1.2 + 1).toInt().coerceAtLeast(required)
            fieldEnds = fieldEnds.copyOf(nextSize)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return toCsv()
    }
}