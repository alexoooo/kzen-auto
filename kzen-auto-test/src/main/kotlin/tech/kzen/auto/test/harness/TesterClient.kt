package tech.kzen.auto.test.harness

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule


/**
 * Minimal HTTP client over the tester kzen-auto's `/logic/...` REST surface.
 *
 * Endpoints used:
 *  - GET /logic/startRun?path={documentPath}&object={objectPath}[&pauseOnError=true]
 *      → runId text or HTTP 400
 *  - GET /logic/status                                            → JSON {time, active}
 *
 * `active == null` (serialized as the string "null" in the response) means the run completed.
 * With `pauseOnError=true`, a step failure parks the run instead of ending it, so
 * `active.state == "Paused"` means the run hit a step error — nothing else pauses runs in
 * this harness (no pause commands are ever sent).
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
    fun startRun(
        documentPath: String,
        objectPath: String = "main",
        pauseOnError: Boolean = false
    ): String = runBlocking {
        val response = http.get("$baseUrl/logic/startRun") {
            parameter("path", documentPath)
            parameter("object", objectPath)
            if (pauseOnError) {
                parameter("pauseOnError", "true")
            }
        }
        val body = response.bodyAsText()
        check(response.status == HttpStatusCode.OK) {
            "startRun failed (${response.status}): $body"
        }
        body.trim()
    }


    /**
     * Block until the current run settles — completes (status.active becomes "null") or parks
     * on a step error (active.state becomes "Paused" under pauseOnError) — checking every
     * [pollIntervalMs]. Returns the final status payload for the caller to inspect.
     */
    fun awaitSettled(timeoutMs: Long = 120_000, pollIntervalMs: Long = 250): Map<String, Any?> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val status = status()
            if (isCompleted(status) || isPaused(status)) {
                return status
            }
            Thread.sleep(pollIntervalMs)
        }
        throw IllegalStateException(
            "logic run did not settle within ${timeoutMs}ms; last status: ${status()}")
    }


    /**
     * Await settle, then assert the run rooted at [documentPath]#[objectPath] recorded NO step error —
     * the per-test "expect success". Unlike a bare `active == null` check (which goes null even when a
     * step threw — see kzen-auto-test/AGENTS.md), this inspects every step's traced [StepTrace.error], so
     * it catches failures whether the run terminated or parked, at any nesting depth, regardless of the
     * run's pauseOnError mode.
     */
    fun awaitSuccess(documentPath: String, objectPath: String = "main", timeoutMs: Long = 120_000) {
        awaitSettled(timeoutMs)
        val errors = runStepErrors(documentPath, objectPath)
        check(errors.isEmpty()) {
            "expected a clean run of $documentPath#$objectPath, but ${errors.size} step(s) errored: $errors"
        }
    }


    /**
     * Await settle, then assert the run rooted at [documentPath]#[objectPath] recorded at least one step
     * error — the per-test "expect failure". Returns the errors for further inspection.
     */
    fun awaitFailure(
        documentPath: String, objectPath: String = "main", timeoutMs: Long = 120_000
    ): List<TracedStepError> {
        awaitSettled(timeoutMs)
        val errors = runStepErrors(documentPath, objectPath)
        check(errors.isNotEmpty()) {
            "expected $documentPath#$objectPath to fail, but no step recorded an error"
        }
        return errors
    }


    /**
     * Every step that recorded an error across the most recent run rooted at [documentPath]#[objectPath].
     * Queries the whole run (`actionLookupRun` merges all sub-script executions) with an empty-prefix
     * [LogicTraceQuery] (matches all paths), then decodes each step trace ($stable-keyed paths only) and
     * keeps the ones with a non-null [StepTrace.error]. Empty if the root has no recorded run yet.
     */
    fun runStepErrors(documentPath: String, objectPath: String = "main"): List<TracedStepError> {
        val root = ObjectLocation(DocumentPath.parse(documentPath), ObjectPath.parse(objectPath))

        val mostRecent = detached(
            CommonRestApi.paramAction to LogicConventions.actionMostRecent,
            LogicConventions.paramSubDocumentPath to root.documentPath.asString(),
            LogicConventions.paramSubObjectPath to root.objectPath.asString())
        check(mostRecent is ExecutionSuccess) {
            "mostRecent failed: ${(mostRecent as? ExecutionFailure)?.errorMessage}"
        }
        @Suppress("UNCHECKED_CAST")
        val runExecutionCollection = mostRecent.value.get() as? Map<String, String>
            ?: return listOf()
        val runId = LogicConventions.runExecutionFromCollection(runExecutionCollection).logicRunId

        val lookup = detached(
            CommonRestApi.paramAction to LogicConventions.actionLookupRun,
            CommonRestApi.paramRunId to runId.value,
            LogicConventions.paramQuery to LogicTraceQuery(LogicTracePath.root).asString())
        check(lookup is ExecutionSuccess) {
            "lookupRun failed: ${(lookup as? ExecutionFailure)?.errorMessage}"
        }
        @Suppress("UNCHECKED_CAST")
        val snapshotCollection = lookup.value.get() as Map<String, Map<String, Any>>
        val snapshot = LogicTraceSnapshot.ofCollection(snapshotCollection)

        return snapshot.values.entries
            // Step traces are keyed by ObjectStableId ($stable-prefixed); skip convention paths
            // (e.g. the "next to run" marker) that don't decode to a StepTrace.
            .filter { it.key.objectStableId() != null }
            .mapNotNull { (path, entry) ->
                StepTrace.ofExecutionValue(entry.value).error
                    ?.let { TracedStepError(path.asString(), it) }
            }
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


    /**
     * Read the display value a step traced during the most recent run, via the `LogicTraceEndpoint`
     * detached action (same surface the browser UI uses). [documentPath] is the sub-script the step
     * lives in (its `#main` root is a logic execution root); [objectPath] is the step within it.
     *
     * No-rename run ⇒ a step's stable id equals its `ObjectLocation.asString()` (see ObjectStableMapper),
     * so the step's trace path is reconstructable here without the server's stable-id mapper.
     */
    fun readDisplayedValue(documentPath: String, objectPath: String): String {
        val subScriptRoot = ObjectLocation(DocumentPath.parse(documentPath), ObjectPath.parse("main"))

        val mostRecent = detached(
            CommonRestApi.paramAction to LogicConventions.actionMostRecent,
            LogicConventions.paramSubDocumentPath to subScriptRoot.documentPath.asString(),
            LogicConventions.paramSubObjectPath to subScriptRoot.objectPath.asString())
        check(mostRecent is ExecutionSuccess) {
            "mostRecent failed: ${(mostRecent as? ExecutionFailure)?.errorMessage}"
        }
        @Suppress("UNCHECKED_CAST")
        val runExecutionCollection = mostRecent.value.get() as? Map<String, String>
            ?: error("no run found for $documentPath#main")
        val runId = LogicConventions.runExecutionFromCollection(runExecutionCollection).logicRunId

        val stepLocation = ObjectLocation(DocumentPath.parse(documentPath), ObjectPath.parse(objectPath))
        val stepTracePath = LogicTracePath.ofObjectStableId(ObjectStableId(stepLocation.asString()))

        val lookup = detached(
            CommonRestApi.paramAction to LogicConventions.actionLookupRun,
            CommonRestApi.paramRunId to runId.value,
            LogicConventions.paramQuery to LogicTraceQuery(stepTracePath).asString())
        check(lookup is ExecutionSuccess) {
            "lookupRun failed: ${(lookup as? ExecutionFailure)?.errorMessage}"
        }
        @Suppress("UNCHECKED_CAST")
        val snapshotCollection = lookup.value.get() as Map<String, Map<String, Any>>
        val snapshot = LogicTraceSnapshot.ofCollection(snapshotCollection)

        val entry = snapshot.values.values.singleOrNull()
            ?: error("expected exactly one trace entry for $objectPath, got: ${snapshot.values.keys}")

        val displayValue = StepTrace.ofExecutionValue(entry.value).displayValue
        check(displayValue is TextExecutionValue) {
            "expected a text display value for $objectPath, got: $displayValue"
        }
        return displayValue.value
    }


    private fun detached(vararg params: Pair<String, String>): ExecutionResult = runBlocking {
        val endpoint = LogicConventions.logicTraceEndpointLocation
        val response = http.get("$baseUrl${CommonRestApi.actionDetached}") {
            parameter(CommonRestApi.paramDocumentPath, endpoint.documentPath.asString())
            parameter(CommonRestApi.paramObjectPath, endpoint.objectPath.asString())
            for ((key, value) in params) {
                parameter(key, value)
            }
        }
        val body = response.bodyAsText()
        check(response.status == HttpStatusCode.OK) {
            "detached failed (${response.status}): $body"
        }
        @Suppress("UNCHECKED_CAST")
        val collection = mapper.readValue(body, Map::class.java) as Map<String, Any?>
        ExecutionResult.fromJsonCollection(collection)
    }


    private fun isCompleted(status: Map<String, Any?>): Boolean {
        // Server serializes a missing active run as the JSON string "null" (see LogicStatus).
        val active = status["active"]
        return active == null || active == "null"
    }


    private fun isPaused(status: Map<String, Any?>): Boolean {
        return (status["active"] as? Map<*, *>)?.get("state") == "Paused"
    }


    override fun close() {
        http.close()
    }
}


/** A step that recorded an error during a run: its trace path and the error message. */
data class TracedStepError(
    val tracePath: String,
    val message: String
)
