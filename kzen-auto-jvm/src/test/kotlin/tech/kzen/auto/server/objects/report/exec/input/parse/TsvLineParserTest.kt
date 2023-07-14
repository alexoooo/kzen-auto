package tech.kzen.auto.server.objects.report.exec.input.parse

import org.junit.Test
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.input.parse.tsv.TsvFormatUtils
import tech.kzen.auto.server.objects.report.exec.input.parse.tsv.TsvReportDefiner
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class TsvLineParserTest {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val tsvUtf8Line = "7088746062\t" +
                "https://greensboro.craigslist.org/ctd/d/cary-2012-acura-tl-base-4dr-sedan/7088746062.html\t" +
                "greensboro\thttps://greensboro.craigslist.org\t10299\t2012\tacura\ttl\t\t\tgas\t90186\tclean\t" +
                "automatic\t19UUA8F22CA003926\t\t\tother\tblue\t" +
                "https://images.craigslist.org/01414_3LIXs9EO33z_600x450.jpg\t" +
                "2012 Acura TL Base 4dr Sedan     Offered by: Best Import Auto Sales Inc — (919) 800-0650 — $10,299" +
                "     PRISTINE CONDITION INSIDE AND OUT   Best Import Auto Sales Inc    Year: 2012 Make: Acura Model" +
                ": TL Series: Base 4dr Sedan VIN: 19UUA8F22CA003926  Condition: Used Mileage: 90,186  Exterior: Blue" +
                " Interior: Black Body: Sedan Transmission: Automatic 6-Speed Engine: 3.5L V6      **** Best Import " +
                "Auto Sales Inc. 🚘 Raleigh Auto Dealer *****  ⚡️⚡️⚡️ Call Or Text (919) 800-0650 ⚡️⚡️⚡️  ✅ - We " +
                "can arrange Financing Options with most banks and credit unions!!!!     ✅ Extended Warranties " +
                "Available on most vehicles!! \"Call To Inquire\"  ✅ Full Service ASE-Certified Shop Onsite!       " +
                "More vehicle details: best-import-auto-sales-inc.hammerwebsites.net/v/cfoamRwq     Address: 1501 " +
                "Buck Jones Rd Raleigh, NC 27606   Phone: (919) 800-0650     Website: www.bestimportsonline.com     " +
                " 📲 ☎️ Call or text (919) 800-0650 for quick answers to your questions about this Acura TL Your " +
                "message will always be answered by a real human — never an automated system.     Disclaimer: Best " +
                "Import Auto Sales Inc will never sell, share, or spam your mobile number. Standard text messaging " +
                "rates may apply.       2012 Acura TL Base 4dr Sedan   30b9c4702111452eb57503c99e795660\t" +
                "\tnc\t35.7636\t-78.7443"
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun emptyValue() {
        val tsvLines = ""
        val read = read(tsvLines)
        assertTrue(read.isEmpty())
    }


    @Test
    fun emptyLines() {
        val tsvLines = "\r\n\r\n\n\n\n\r\n"
        assertTrue(read(tsvLines).isEmpty())
        for (i in 1 .. 9) {
            assertTrue(read(tsvLines, i).isEmpty())
        }
    }


    @Test
    fun tsvEmptyValues() {
        val tsvLines = "\t"
        assertEquals(listOf("", ""), read(tsvLines)[0].toList())
        assertEquals(tsvLines, TsvFormatUtils.toTsv(read(tsvLines)[0]))
    }


    @Test
    fun mannyEmptyValues() {
        val tsvLines = "\t\t\t\t\t\t\t\t\t"
        val values = listOf("", "", "", "", "", "", "", "", "", "")
        assertEquals(values, read(tsvLines)[0].toList())
        for (i in 1 .. 10) {
            assertEquals(values, read(tsvLines, i)[0].toList())
        }
    }


    @Test
    fun singleCharacterValue() {
        val tsvLine = "a"
        assertEquals(listOf("a"), read(tsvLine)[0].toList())
    }


    @Test
    fun fewCharacterValue() {
        val tsvLine = "abcde"
        assertEquals(listOf("abcde"), read(tsvLine)[0].toList())
        for (i in 1 .. 4) {
            assertEquals(listOf("abcde"), read(tsvLine, i)[0].toList())
        }
    }


    @Test
    fun twoSingleCharacterValues() {
        val tsvLine = "a\tb"
        assertEquals(listOf("a", "b"), read(tsvLine)[0].toList())
    }


    @Test
    fun fiveSingleCharacterValues() {
        val tsvLine = "a\tb\tc\td\te"
        assertEquals(listOf("a", "b", "c", "d", "e"), read(tsvLine)[0].toList())
        for (i in 1 .. 8) {
            assertEquals(listOf("a", "b", "c", "d", "e"), read(tsvLine, i)[0].toList())
        }
    }


    @Test
    fun simpleBareLine() {
        val tsvLine = "id\turl\tregion\tregion_url\tprice\tyear\tmanufacturer\tmodel\tcondition\tcylinders\tfuel\t" +
                "odometer\ttitle_status\ttransmission\tvin\tdrive\tsize\ttype\tpaint_color\timage_url\tdescription\t" +
                "county\tstate\tlat\tlong"

        val cells = tsvLine.split("\t")

        assertEquals(cells, read(tsvLine)[0].toList())
        for (i in 1 .. 16) {
            assertEquals(cells, read(tsvLine, i)[0].toList())
        }
    }


    @Test
    fun simpleTwoLineWithTrailer() {
        val tsvLines = "foo\tbar\r\nhello\t1\r\n"
        val parsed = read(tsvLines)
        assertEquals(listOf("foo", "bar"), parsed[0].toList())
        assertEquals(listOf("hello", "1"), parsed[1].toList())
        for (i in 1 .. 18) {
            val bufferParsed = read(tsvLines, i)
            assertEquals(listOf("foo", "bar"), bufferParsed[0].toList())
            assertEquals(listOf("hello", "1"), bufferParsed[1].toList())
        }
    }


    @Test
    fun simpleFourLineWithoutTrailer() {
        val tsvLines = "foo\tbar\r\nhello\t1\nworld\t42\r\nworld\t420"
        assertEquals(4, read(tsvLines).size)
        for (i in 1 .. 38) {
            assertEquals(4, read(tsvLines).size)
        }
    }


    @Test
    fun utf8Line() {
        val line = read(tsvUtf8Line)[0]
        assertEquals(25, line.toList().size)
        assertEquals(tsvUtf8Line, TsvFormatUtils.toTsv(line))

        for (i in 1 .. 16) {
            assertEquals(
                tsvUtf8Line, TsvFormatUtils.toTsv(read(
                    tsvUtf8Line, i)[0]))
        }
    }


    @Test
    fun utf8Lines() {
        val tsvLineA = tsvUtf8Line
        val tsvLineB = tsvUtf8Line
        val lines = "$tsvLineA\r\n$tsvLineB"

        val parsed = read(lines)
        assertEquals(2, parsed.size)
        assertEquals(tsvLineA, TsvFormatUtils.toTsv(parsed[0]))
        assertEquals(tsvLineB, TsvFormatUtils.toTsv(parsed[1]))

        for (i in 1 .. 128) {
            val parsedBuffered = read(lines, i)
            assertEquals(2, parsedBuffered.size)
            assertEquals(tsvLineA, TsvFormatUtils.toTsv(parsedBuffered[0]))
            assertEquals(tsvLineB, TsvFormatUtils.toTsv(parsedBuffered[1]))
        }
    }


    @Test
    fun utf8LinesFollowedByEmpty() {
        val tsvLineA = tsvUtf8Line
        val tsvLineB = tsvUtf8Line
        val lines = "$tsvLineA\r\n$tsvLineB\r\n"

        val parsed = read(lines)
        assertEquals(2, parsed.size)
        assertEquals(tsvLineA, TsvFormatUtils.toTsv(parsed[0]))
        assertEquals(tsvLineB, TsvFormatUtils.toTsv(parsed[1]))

        for (i in 1 .. 128) {
            val parsedBuffered = read(lines, i)
            assertEquals(2, parsedBuffered.size)
            assertEquals(tsvLineA, TsvFormatUtils.toTsv(parsedBuffered[0]))
            assertEquals(tsvLineB, TsvFormatUtils.toTsv(parsedBuffered[1]))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun read(
        text: String,
        bufferSize: Int = text.length
    ): List<FlatFileRecord> {
        return TsvReportDefiner.literal(text, bufferSize)
    }
}