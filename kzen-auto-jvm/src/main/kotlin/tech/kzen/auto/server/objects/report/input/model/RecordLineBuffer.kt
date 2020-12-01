package tech.kzen.auto.server.objects.report.input.model

import tech.kzen.auto.server.objects.report.input.parse.FastCsvLineParser
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.Writer


class RecordLineBuffer(
    expectedContentLength: Int = 0,
    expectedFieldCount: Int = 0
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun toCsv(values: List<String>): String {
            return of(values).toCsv()
        }

        fun of(values: List<String>): RecordLineBuffer {
            val buffer = RecordLineBuffer()

            for (value in values) {
                for (i in value.indices) {
                    buffer.addToField(value[i])
                }
                buffer.commitField()
            }

            return buffer
        }

        fun of(contents: CharArray, offset: Int, length: Int): RecordLineBuffer {
            val buffer = RecordLineBuffer(length, 1)
            contents.copyInto(buffer.fieldContents, 0, offset, offset + length)
            buffer.fieldCount = 1
            buffer.fieldEnds[0] = length
            buffer.fieldContentLength = length
            return buffer
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    internal var fieldContents = CharArray(expectedContentLength)
    private var fieldEnds = IntArray(expectedFieldCount)
    private var fieldCount = 0
    private var fieldContentLength = 0

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


    fun isEmpty(): Boolean {
        return fieldCount == 0 && fieldContentLength == 0
    }


    fun toList(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until fieldCount) {
            val item = getString(i)
            list.add(item)
        }
        return list
    }


    fun toCsv(): String {
        val out = ByteArrayOutputStream()
        OutputStreamWriter(out, Charsets.UTF_8).use {
            writeCsv(it)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }


    fun writeCsv(out: Writer) {
        for (i in 0 until fieldCount) {
            if (i != 0) {
                out.write(FastCsvLineParser.delimiter)
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

        FastCsvLineParser.writeCsv(fieldContents, startIndex, endIndex, out);
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
    fun addToField(nextChar: Char) {
        growFieldContentsIfRequired()
        fieldContents[fieldContentLength] = nextChar
        fieldContentLength++
    }


    fun commitField() {
        growFieldEndsIfRequired()

        fieldEnds[fieldCount] = fieldContentLength
        fieldCount++
    }


    fun clear() {
        fieldCount = 0
        fieldContentLength = 0
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun growFieldContentsIfRequired() {
        if (fieldContents.size > fieldContentLength) {
            return
        }

        val nextSize = (fieldContents.size * 1.2).toInt() + 1
        fieldContents = fieldContents.copyOf(nextSize)
    }


    private fun growFieldEndsIfRequired() {
        if (fieldEnds.size > fieldCount) {
            return
        }

        val nextSize = (fieldEnds.size * 1.2).toInt() + 1
        fieldEnds = fieldEnds.copyOf(nextSize)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return toCsv()
    }
}