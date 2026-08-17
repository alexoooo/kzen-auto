package tech.kzen.auto.server.api.handler

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.AfterClass
import org.junit.BeforeClass
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.ktorMain
import tech.kzen.auto.server.objects.report.exec.output.flat.IndexedCsvTable
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * Route-level test of `GET /job/download`, driving the REAL plugin stack and route table
 * ([tech.kzen.auto.server.ktorMain]) in-process through `testApplication` — so it covers the transport layer
 * that a [DetachedActionHandler] unit test cannot see: status, the attachment header, and the Compression
 * plugin's effect on the streamed body.
 *
 * Hermetic with respect to notation: [DetachedActionHandler.jobDownload] resolves the Worker's persistent
 * output dir from the parsed [ObjectLocation] alone ([tech.kzen.auto.server.objects.job.service.JobWorkPool]),
 * so the addressed document/object need not exist in the graph and the fixture is a table written straight
 * into that dir.
 */
class JobDownloadRouteTest {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val probeLocation = ObjectLocation(
            DocumentPath.parse("test/job/job-download-route-test.yaml"),
            ObjectPath.parse("main.workers/DownloadRouteProbe"))

        private val absentLocation = ObjectLocation(
            DocumentPath.parse("test/job/job-download-route-test.yaml"),
            ObjectPath.parse("main.workers/DownloadRouteAbsent"))

        // The fixture must clear install(Compression)'s 1024-byte minimumSize, or compressionProbe would
        // observe "no encoding" for a reason unrelated to content type.
        private const val fixtureRowCount = 300

        // FormatUtils.sanitizeFilename + DateTimeUtils.filenameTimestamp ("yyyyMMdd_HHmmss_SSS") — the
        // timestamp is wall-clock, so the header can only be matched, never compared.
        private val contentDispositionPattern =
            Regex("""^attachment; filename\*=utf-8''DownloadRouteProbe_\d{8}_\d{6}_\d{3}\.csv$""")

        // KzenAutoContext parses the whole notation corpus and validates the service environment, so one
        // instance serves the class rather than one per test method.
        private lateinit var context: KzenAutoContext
        private lateinit var probeDir: Path


        @BeforeClass
        @JvmStatic
        fun setUp() {
            context = KzenAutoContext.forTest()

            probeDir = context.jobWorkPool.workerOutputDir(probeLocation)
            Files.createDirectories(probeDir)

            val header = HeaderListing.of(listOf("city", "amount"))
            val table = IndexedCsvTable(header, probeDir)
            try {
                for (row in 0 until fixtureRowCount) {
                    table.add(FlatFileRecord.of(listOf("Metropolis-$row", (row * 7).toString())), header)
                }
            }
            finally {
                table.close(error = false)
            }
        }


        @AfterClass
        @JvmStatic
        fun tearDown() {
            // A Worker's output dir is persistent by contract (JobWorkPool sweeps neither on settle nor on
            // boot), so the test owns its removal.
            if (Files.exists(probeDir)) {
                WorkUtils.recursivelyDeleteDir(probeDir)
            }
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun withRoutes(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { ktorMain(context) }
            block()
        }


    private suspend fun ApplicationTestBuilder.download(
        location: ObjectLocation,
        acceptEncoding: String? = null
    ) =
        client.get(CommonRestApi.jobDownload) {
            parameter(CommonRestApi.paramDocumentPath, location.documentPath.asString())
            parameter(CommonRestApi.paramObjectPath, location.objectPath.asString())
            acceptEncoding?.let { header(HttpHeaders.AcceptEncoding, it) }
        }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun servesThePersistedTableAsAnAttachment() = withRoutes {
        val response = download(probeLocation)

        assertEquals(HttpStatusCode.OK, response.status)

        // The un-percent-encoded `filename*` form is malformed under RFC 5987 for a non-ASCII name; this
        // asserts it as safe-by-sanitization, not as spec-correct in general — FormatUtils.sanitizeFilename
        // collapses the Worker name to [a-zA-Z0-9_-], which needs no encoding.
        val contentDisposition = response.headers[HttpHeaders.ContentDisposition]
        assertTrue(
            contentDisposition != null && contentDispositionPattern.matches(contentDisposition),
            "Unexpected Content-Disposition: $contentDisposition")

        assertContentEquals(
            Files.readAllBytes(IndexedCsvTable.tablePath(probeDir)),
            response.bodyAsBytes())
    }


    @Test
    fun answersInternalServerErrorWhenNothingIsPersisted() = withRoutes {
        // Pinning pre-existing behaviour, not endorsing it: jobDownload raises a bare IllegalStateException
        // and no StatusPages plugin is installed, so Ktor's default failure path answers 500 with an HTML
        // error page. The exception does NOT propagate out of the client call.
        val response = download(absentLocation)

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }


    @Test
    fun compressesTheAttachmentOnlyForAnEncodingAwareClient() = withRoutes {
        // Observed: text/csv above minimumSize IS gzipped, and compressing drops Content-Length — the body
        // then arrives with no declared length. That holds only when the client offers an encoding; a client
        // that sends no Accept-Encoding gets the file uncompressed WITH its exact length.
        val compressed = download(probeLocation, "gzip")
        assertEquals("gzip", compressed.headers[HttpHeaders.ContentEncoding])
        assertNull(compressed.headers[HttpHeaders.ContentLength])

        val identity = download(probeLocation)
        assertNull(identity.headers[HttpHeaders.ContentEncoding])
        assertEquals(
            Files.size(IndexedCsvTable.tablePath(probeDir)).toString(),
            identity.headers[HttpHeaders.ContentLength])
    }
}
