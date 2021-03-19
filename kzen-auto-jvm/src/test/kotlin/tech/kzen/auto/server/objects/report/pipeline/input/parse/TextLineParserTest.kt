package tech.kzen.auto.server.objects.report.pipeline.input.parse

import org.junit.Test
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.parse.text.TextProcessorDefiner
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class TextLineParserTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun emptyFile() {
        val textLines = ""
        val read = read(textLines)
        assertEquals(1, read.size)
        assertEquals(1, read[0].fieldCount())
        assertTrue(read[0].getString(0).isEmpty())
    }


    @Test
    fun twoEmptyLinesWindows() {
        val textLines = "\r\n"
        val read = read(textLines)
        assertEquals(2, read.size)
        assertTrue(read[0].getString(0).isEmpty())
        assertTrue(read[1].getString(0).isEmpty())
    }


    @Test
    fun threeEmptyLinesWindows() {
        val textLines = "\r\n\r\n"
        for (i in 1 .. textLines.length) {
            val read = read(textLines, i)
            assertEquals(3, read.size)
            for (j in 0 until 3) {
                assertTrue(read[j].getString(0).isEmpty())
            }
        }
    }


    @Test
    fun twoEmptyLinesLinux() {
        val textLines = "\n"
        val read = read(textLines)
        assertEquals(2, read.size)
        assertTrue(read[0].getString(0).isEmpty())
        assertTrue(read[1].getString(0).isEmpty())
    }


    @Test
    fun threeEmptyLinesLinux() {
        val textLines = "\n\n"
        for (i in 1 .. textLines.length) {
            val read = read(textLines, i)
            assertEquals(3, read.size)
            for (j in 0 until 3) {
                assertTrue(read[j].getString(0).isEmpty())
            }
        }
    }


    @Test
    fun emptyLinesMixedShort() {
        val textLines = "\r\n\n"
//        assertEquals(7, read(textLines, 1).size)
        assertEquals(3, read(textLines).size)
        for (i in 1 .. textLines.length) {
            val read = read(textLines, i)
            assertEquals(3, read.size)
            assertTrue(read.all { it.getString(0).isEmpty() })
        }
    }


    @Test
    fun emptyLinesMixed() {
        val textLines = "\r\n\r\n\n\n\n\r\n"
        assertEquals(7, read(textLines).size)
        for (i in 1 .. textLines.length) {
            val read = read(textLines, i)
            assertEquals(7, read.size)
            assertTrue(read.all { it.getString(0).isEmpty() })
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun blankLine() {
        val textLines = " "
        val read = read(textLines)
        assertEquals(1, read.size)
        assertEquals(" ", read[0].getString(0))
    }


    @Test
    fun fewCharacterLine() {
        val textLines = " foo, bar, bar!!  "
        for (i in 1 .. textLines.length) {
            val read = read(textLines, i)
            assertEquals(1, read.size)
            assertEquals(textLines, read[0].getString(0))
        }
    }


    @Test
    fun utf8CharacterLine() {
        val textLines = "\uD83D\uDE98\t✅,,"
        for (i in 1 .. textLines.length) {
            val read = read(textLines, i)
            assertEquals(1, read.size)
            assertEquals(textLines, read[0].getString(0))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun blankLineInBetween() {
        val textLines = "a\nb\r\n\nc"
        for (i in 1 .. textLines.length) {
            val read = read(textLines, i)
            assertEquals(4, read.size)
            assertEquals("a", read[0].getString(0))
            assertEquals("b", read[1].getString(0))
            assertEquals("", read[2].getString(0))
            assertEquals("c", read[3].getString(0))
        }
    }


    @Test
    fun shortMixedLines() {
        val textLines = "aa\r\n\ncc\n\r\nfff"
        for (i in 1 .. textLines.length) {
            val read = read(textLines, i)
            assertEquals(5, read.size)
            assertEquals("aa", read[0].getString(0))
            assertEquals("", read[1].getString(0))
            assertEquals("cc", read[2].getString(0))
            assertEquals("", read[3].getString(0))
            assertEquals("fff", read[4].getString(0))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun read(
        text: String,
        bufferSize: Int = text.length
    ): List<RecordRowBuffer> {
//        return ReportInputChain.allText(text, bufferSize)
        return TextProcessorDefiner.literal(text, bufferSize)
    }
}