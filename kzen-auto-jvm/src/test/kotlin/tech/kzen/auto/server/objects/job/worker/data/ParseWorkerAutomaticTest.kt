package tech.kzen.auto.server.objects.job.worker.data

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.JobReadConventions
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness
import tech.kzen.auto.server.objects.job.worker.content.ContentTestHarness.Companion.collected
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.model.obj.ObjectPath
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.walk
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull


/**
 * `File → Parse(Automatic)` over small `.csv` files: each file's name chooses CSV and its header names the columns,
 * both before Run (the columns and "Read as" format the card shows) and in the run's rows.
 */
class ParseWorkerAutomaticTest {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val document = "test/job/content/parse-automatic-test.yaml"
        private val inDirectory = Path.of("build/parse-automatic/in")

        private val columns = listOf("station", "date", "temperature", "humidity")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val harness = ContentTestHarness()


    @Before
    fun setUp() {
        if (Files.exists(inDirectory)) {
            inDirectory.walk().sortedByDescending { it.nameCount }.forEach { Files.deleteIfExists(it) }
        }
        Files.createDirectories(inDirectory)
        Files.writeString(
            inDirectory.resolve("measurements-1.csv"),
            "station,date,temperature\ns1,2026-01-01,12.5\ns2,2026-01-01,\"14,1\"\ns1,2026-01-02,13\n")
        Files.writeString(
            inDirectory.resolve("measurements-2.csv"),
            "station,date,humidity\ns1,2026-01-01,40\ns2,2026-01-01,55\n")
    }


    @After
    fun tearDown() {
        harness.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun beforeRunTheDetectedHeadersAreTheColumns() {
        val validation = harness.validate(document)
        val parse = assertNotNull(validation.workerValidations[ObjectPath.parse("main.workers/parse")])

        assertNull(parse.errorMessage)
        val record = assertIs<DataType.Record>(assertNotNull(parse.contract).payload().structural)
        assertEquals(columns, record.fields.map { it.id.name })
        assertEquals(
            listOf("measurements-1.csv" to "CSV · UTF-8", "measurements-2.csv" to "CSV · UTF-8"),
            (parse.details[JobReadConventions.namedFormatsKey] as List<*>).map {
                val file = it as Map<*, *>
                file[JobReadConventions.namedFormatFileKey] to file[JobReadConventions.namedFormatLabelKey]
            })

        // Each column with the files that hold it: the card counts those some files lack
        assertEquals(2L, parse.details[JobReadConventions.sampledFilesKey])
        val both = listOf("measurements-1.csv", "measurements-2.csv")
        assertEquals(
            listOf(
                "station" to both,
                "date" to both,
                "temperature" to listOf("measurements-1.csv"),
                "humidity" to listOf("measurements-2.csv")),
            (parse.details[JobReadConventions.sampledColumnsKey] as List<*>).map {
                val column = it as Map<*, *>
                column[JobReadConventions.sampledColumnNameKey] to column[JobReadConventions.sampledColumnFilesKey]
            })
    }


    @Test
    fun theRunReadsEachHeaderAndCombinesTheColumns() {
        val rows = collected(harness.run(document)).map { it as Map<*, *> }

        assertEquals(5, rows.size)
        assertEquals(columns.toSet(), rows.flatMap { it.keys }.toSet())
        assertEquals(listOf("s1", "s2", "s1", "s1", "s2"), rows.map { it["station"] })
        assertEquals("14,1", rows[1]["temperature"])
        assertEquals("55", rows[4]["humidity"])
    }
}
