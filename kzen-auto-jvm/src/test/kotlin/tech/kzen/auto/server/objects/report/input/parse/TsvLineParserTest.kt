package tech.kzen.auto.server.objects.report.input.parse

import org.junit.Test
import tech.kzen.auto.server.objects.report.input.read.RecordLineReader
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class TsvLineParserTest {
    companion object {
        private val tsvUtf8Line = "7088746062\t" +
                "https://greensboro.craigslist.org/ctd/d/cary-2012-acura-tl-base-4dr-sedan/7088746062.html\t" +
                "greensboro\thttps://greensboro.craigslist.org\t10299\t2012\tacura\ttl\t\t\tgas\t90186\tclean\t" +
                "automatic\t19UUA8F22CA003926\t\t\tother\tblue\t" +
                "https://images.craigslist.org/01414_3LIXs9EO33z_600x450.jpg\t" +
                "2012 Acura TL Base 4dr Sedan     Offered by: Best Import Auto Sales Inc — (919) 800-0650 — $10,299" +
                "     PRISTINE CONDITION INSIDE AND OUT   Best Import Auto Sales Inc    Year: 2012 Make: Acura Model" +
                ": TL Series: Base 4dr Sedan VIN: 19UUA8F22CA003926  Condition: Used Mileage: 90,186  Exterior: Blue" +
                " Interior: Black Body: Sedan Transmission: Automatic 6-Speed Engine: 3.5L V6      **** Best Import " +
                "Auto Sales Inc. 🚘 Raleigh Auto Dealer *****  ⚡️⚡️⚡️ Call Or Text (919) 800-0650 ⚡️⚡️⚡️  ✅ - We can " +
                "arrange Financing Options with most banks and credit unions!!!!     ✅ Extended Warranties " +
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
        assertTrue(RecordLineReader.tsvLines(tsvLines).isEmpty())
    }


    @Test
    fun emptyLines() {
        val tsvLines = "\r\n\r\n\n\n\n\r\n"
        assertTrue(RecordLineReader.tsvLines(tsvLines).isEmpty())
        assertTrue(RecordLineReader.tsvLines(tsvLines, 1).isEmpty())
        assertTrue(RecordLineReader.tsvLines(tsvLines, 2).isEmpty())
        assertTrue(RecordLineReader.tsvLines(tsvLines, 3).isEmpty())
    }


    @Test
    fun tsvEmptyValues() {
        val tsvLines = "\t"
        assertEquals(listOf("", ""), RecordLineReader.tsvLines(tsvLines)[0].toList())
        assertEquals(tsvLines, RecordLineReader.tsvLines(tsvLines)[0].toTsv())
    }


    @Test
    fun mannyEmptyValues() {
        val csvLines = "\t\t\t\t\t\t\t\t\t"
        val values = listOf("", "", "", "", "", "", "", "", "", "")
        assertEquals(values, RecordLineReader.tsvLines(csvLines)[0].toList())
        assertEquals(values, RecordLineReader.tsvLines(csvLines, 1)[0].toList())
        assertEquals(values, RecordLineReader.tsvLines(csvLines, 2)[0].toList())
        assertEquals(values, RecordLineReader.tsvLines(csvLines, 3)[0].toList())
        assertEquals(values, RecordLineReader.tsvLines(csvLines, 4)[0].toList())
    }


    @Test
    fun singleCharacterValue() {
        val csvLine = "a"
        assertEquals(listOf("a"), RecordLineReader.tsvLines(csvLine)[0].toList())
    }


    @Test
    fun fewCharacterValue() {
        val csvLine = "abc"
        assertEquals(listOf("abc"), RecordLineReader.tsvLines(csvLine)[0].toList())
        assertEquals(listOf("abc"), RecordLineReader.tsvLines(csvLine, 1)[0].toList())
        assertEquals(listOf("abc"), RecordLineReader.tsvLines(csvLine, 2)[0].toList())
    }


    @Test
    fun twoSingleCharacterValues() {
        val csvLine = "a\tb"
        assertEquals(listOf("a", "b"), RecordLineReader.tsvLines(csvLine)[0].toList())
        assertEquals(listOf("a", "b"), RecordLineReader.tsvLines(csvLine, 1)[0].toList())
        assertEquals(listOf("a", "b"), RecordLineReader.tsvLines(csvLine, 2)[0].toList())
    }


    @Test
    fun simpleBareLine() {
        val tsvLine = "id\turl\tregion\tregion_url\tprice\tyear\tmanufacturer\tmodel\tcondition\tcylinders\tfuel\t" +
                "odometer\ttitle_status\ttransmission\tvin\tdrive\tsize\ttype\tpaint_color\timage_url\tdescription\t" +
                "county\tstate\tlat\tlong"

        val cells = tsvLine.split("\t")

        assertEquals(cells, RecordLineReader.tsvLines(tsvLine)[0].toList())
        for (i in 1 .. 16) {
            assertEquals(cells, RecordLineReader.tsvLines(tsvLine, i)[0].toList())
        }
    }


    @Test
    fun utf8Line() {
        val line = RecordLineReader.tsvLines(tsvUtf8Line)[0]
        assertEquals(25, line.toList().size)
        assertEquals(tsvUtf8Line, line.toTsv())

        for (i in 1 .. 16) {
            assertEquals(tsvUtf8Line, RecordLineReader.tsvLines(tsvUtf8Line, i)[0].toTsv())
        }
    }


    @Test
    fun utf8Lines() {
        val tsvLineA = tsvUtf8Line
        val tsvLineB = tsvUtf8Line
        val lines = "$tsvLineA\r\n$tsvLineB\r\n"

        val parsed = RecordLineReader.tsvLines(lines)
        assertEquals(2, parsed.size)
        assertEquals(tsvLineA, parsed[0].toTsv())
        assertEquals(tsvLineB, parsed[1].toTsv())

        for (i in 1 .. 128) {
            val parsedBuffered = RecordLineReader.tsvLines(lines, i)
            assertEquals(2, parsedBuffered.size)
            assertEquals(tsvLineA, parsedBuffered[0].toTsv())
            assertEquals(tsvLineB, parsedBuffered[1].toTsv())
        }
    }
}