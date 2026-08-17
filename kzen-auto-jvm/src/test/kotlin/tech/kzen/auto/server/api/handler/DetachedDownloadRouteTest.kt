package tech.kzen.auto.server.api.handler

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.AfterClass
import org.junit.BeforeClass
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.server.api.handler.test.MissingFileDownloadAction
import tech.kzen.auto.server.api.handler.test.StreamedCsvDownloadAction
import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.ktorMain
import tech.kzen.auto.server.kzenAutoJsModuleName
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * Route-level test of `GET /action/download`, driving the REAL plugin stack and route table
 * ([tech.kzen.auto.server.ktorMain]) in-process through `testApplication` — covering the two transport
 * behaviours a [DetachedActionHandler] unit test cannot see: a body streamed straight into the response, and
 * the existence guard that keeps a missing file from becoming a truncated success.
 *
 * Reaching the route at all takes a purpose-built module root. `AutoConventions.serverAllowed` admits only
 * `kzen/`, `auto-common/`, `auto-jvm/` and `main/`, so a fixture action in `test/` notation is filtered out
 * before [tech.kzen.auto.server.service.exec.ModelDetachedExecutor] can instantiate it. Pointing
 * [KzenAutoConfig.moduleRoot] at a temporary directory makes that directory the only file notation the server
 * reads or writes, so the fixture document can live under `main/` without going near the checked-out `main/`
 * tree. The bundled `main/` is excluded from the classpath corpus, so the two document sets stay disjoint and
 * the framework archetypes still resolve.
 */
class DetachedDownloadRouteTest {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val fixtureDocumentName = "detached-download-route-test.yaml"

        private val fixtureNotation = """
            StreamedCsvDownloadAction:
              class: ${StreamedCsvDownloadAction::class.java.name}

            MissingFileDownloadAction:
              class: ${MissingFileDownloadAction::class.java.name}
            """.trimIndent()

        private val fixtureDocumentPath = DocumentPath.parse("main/$fixtureDocumentName")

        private val writerLocation = ObjectLocation(
            fixtureDocumentPath, ObjectPath.parse("StreamedCsvDownloadAction"))

        private val missingFileLocation = ObjectLocation(
            fixtureDocumentPath, ObjectPath.parse("MissingFileDownloadAction"))

        private val contentDispositionPattern = Regex(
            """^attachment; filename\*=utf-8''""" + Regex.escape(StreamedCsvDownloadAction.fileName) + "$")

        // KzenAutoContext parses the whole notation corpus and validates the service environment, so one
        // instance serves the class rather than one per test method.
        private lateinit var moduleRoot: Path
        private lateinit var context: KzenAutoContext


        @BeforeClass
        @JvmStatic
        fun setUp() {
            moduleRoot = Files.createTempDirectory("kzen-detached-download-route-test")

            // GradleLocator's single scan root under the override, so the fixture is the whole file notation.
            val notationDir = moduleRoot.resolve("src/main/resources/notation/main")
            Files.createDirectories(notationDir)
            Files.writeString(notationDir.resolve(fixtureDocumentName), fixtureNotation)

            context = KzenAutoContext.create(KzenAutoConfig(
                jsModuleName = kzenAutoJsModuleName,
                moduleRoot = moduleRoot))
        }


        @AfterClass
        @JvmStatic
        fun tearDown() {
            context.close()
            WorkUtils.recursivelyDeleteDir(moduleRoot)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun withRoutes(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { ktorMain(context) }
            block()
        }


    private suspend fun ApplicationTestBuilder.download(location: ObjectLocation) =
        client.get(CommonRestApi.actionDetachedDownload) {
            parameter(CommonRestApi.paramDocumentPath, location.documentPath.asString())
            parameter(CommonRestApi.paramObjectPath, location.objectPath.asString())
        }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun servesTheGeneratedPayloadAsAnAttachment() = withRoutes {
        val response = download(writerLocation)

        assertEquals(HttpStatusCode.OK, response.status)

        val contentDisposition = response.headers[HttpHeaders.ContentDisposition]
        assertTrue(
            contentDisposition != null && contentDispositionPattern.matches(contentDisposition),
            "Unexpected Content-Disposition: $contentDisposition")

        assertEquals(StreamedCsvDownloadAction.payload, response.bodyAsText())
    }


    @Test
    fun refusesADownloadWhoseFileIsMissing() = withRoutes {
        // The property being pinned: a missing OfFile path fails BEFORE anything is written to the response,
        // so the client gets a bare error instead of a 200 carrying a truncated body. LocalFileContent opens
        // the file only at body transfer, which is too late to change the answer.
        //
        // The absent attachment header is half the guarantee, not a detail: a browser following an <a href>
        // saves whatever the response carries when Content-Disposition says attachment, so an error page under
        // that header would land on disk as the .csv the user asked for. No StatusPages plugin is installed,
        // so the bare IllegalStateException takes Ktor's default failure path as 500.
        val response = download(missingFileLocation)

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertNull(response.headers[HttpHeaders.ContentDisposition])
        assertNotEquals(
            ContentType.parse(ExecutionDownloadResult.mimeTypeCsv),
            response.contentType()?.withoutParameters())
    }
}
