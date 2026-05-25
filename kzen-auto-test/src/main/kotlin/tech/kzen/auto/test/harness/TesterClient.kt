package tech.kzen.auto.test.harness

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule


/**
 * Minimal HTTP client over the tester kzen-auto's `/logic/...` REST surface.
 *
 * Endpoints used:
 *  - GET /logic/startRun?path={documentPath}&object={objectPath}  → runId text or HTTP 400
 *  - GET /logic/status                                            → JSON {time, active}
 *
 * `active == null` (serialized as the string "null" in the response) means the run completed.
 */
class TesterClient(testerPort: Int):
    AutoCloseable
{
    private val baseUrl = "http://127.0.0.1:$testerPort"
    private val mapper: ObjectMapper = JsonMapper.builder().addModule(kotlinModule()).build()

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson()
        }
        expectSuccess = false
    }


    /**
     * Start a logic-run on the given Script ObjectLocation. Returns the run id on success.
     * Throws if the tester returns non-2xx (e.g. unknown ObjectLocation, graph errors).
     */
    fun startRun(documentPath: String, objectPath: String = "main"): String = runBlocking {
        val response = http.get("$baseUrl/logic/startRun") {
            parameter("path", documentPath)
            parameter("object", objectPath)
        }
        val body = response.bodyAsText()
        check(response.status == HttpStatusCode.OK) {
            "startRun failed (${response.status}): $body"
        }
        body.trim()
    }


    /**
     * Block until the current run completes (status.active becomes "null"), checking every
     * [pollIntervalMs]. Returns the final non-active status payload for the caller to inspect.
     */
    fun awaitCompletion(timeoutMs: Long = 120_000, pollIntervalMs: Long = 250): Map<String, Any?> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val status = status()
            if (isCompleted(status)) {
                return status
            }
            Thread.sleep(pollIntervalMs)
        }
        throw IllegalStateException(
            "logic run did not complete within ${timeoutMs}ms; last status: ${status()}")
    }


    fun status(): Map<String, Any?> = runBlocking {
        val response = http.get("$baseUrl/logic/status")
        val body = response.bodyAsText()
        check(response.status == HttpStatusCode.OK) {
            "status failed (${response.status}): $body"
        }
        @Suppress("UNCHECKED_CAST")
        mapper.readValue(body, Map::class.java) as Map<String, Any?>
    }


    private fun isCompleted(status: Map<String, Any?>): Boolean {
        // Server serializes a missing active run as the JSON string "null" (see LogicStatus).
        val active = status["active"]
        return active == null || active == "null"
    }


    override fun close() {
        http.close()
    }
}
