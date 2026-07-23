package tech.kzen.auto.server

import com.google.common.io.ByteStreams
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.api.staticResourceDir
import tech.kzen.auto.common.api.staticResourcePath
import tech.kzen.auto.server.api.IconCollectionHandler
import tech.kzen.auto.server.api.handler.DetachedActionHandler
import tech.kzen.auto.server.api.handler.FileListingHandler
import tech.kzen.auto.server.api.handler.LogicHandler
import tech.kzen.auto.server.api.handler.NotationQueryHandler
import tech.kzen.auto.server.api.handler.ObjectStableHandler
import tech.kzen.auto.server.api.handler.StorageHandler
import tech.kzen.auto.server.api.handler.TaskHandler
import tech.kzen.auto.server.api.handler.command.*
import tech.kzen.auto.server.backend.indexPage
import tech.kzen.auto.server.context.BuildInfo
import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.service.impl.LogicStartAttempt
import tech.kzen.lib.common.util.ImmutableByteArray
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds


//---------------------------------------------------------------------------------------------------------------------
const val kzenAutoJsModuleName = "kzen-auto-js"
//const val jsResourcePath = "$staticResourcePath/$jsFileName"

private const val indexFileName = "index.html"
private const val indexFilePath = "/$indexFileName"


//---------------------------------------------------------------------------------------------------------------------
fun main(args: Array<String>) {
    val context = kzenAutoInit(args, kzenAutoJsModuleName, BuildInfo.load("/kzen-auto-build.properties"))
    kzenAutoMain(context)
}


// Managed-child lifeline. Wired from kzenAutoInit (below) — NOT from each main() — so every kzen-auto-
//  based server inherits it: kzen-auto's own KzenAutoMain AND kzen-project's KzenProjectMain, which
//  reaches the server only through kzenAutoInit/kzenAutoMain and previously (silently) had no reaper, so
//  project children orphaned the shell. (kzen-launcher's KzenLauncherMain intentionally duplicates these
//  ~20 lines — it depends on neither kzen-lib nor kzen-auto, so there's no shared home worth the coupling.)
//  The spawning parent keeps our stdin open as a PIPE; when it closes that stream (graceful stop) or dies
//  (the OS then closes the inherited pipe on every platform) we observe EOF. The parent may also send a
//  "SHUTDOWN" line. Either way exitProcess(0) runs the shutdown hook registered in kzenAutoInit
//  (context.close()) for a graceful, resource-disposing exit — OS-agnostic, unlike Process.destroy()
//  which is a hookless hard kill (TerminateProcess) on Windows.
private fun startManagedLifeline() {
    val thread = Thread({
        try {
            System.`in`.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break  // null == EOF (pipe closed / parent gone)
                    if (line.trim() == "SHUTDOWN") {
                        break
                    }
                }
            }
        }
        catch (ignored: Throwable) {
            // A read failure (e.g. the parent vanished mid-read) is itself a death signal.
        }
        exitProcess(0)
    }, "kzen-managed-lifeline")
    thread.isDaemon = true
    thread.start()
}


// Backup reaper: self-exit if the parent process exits, even if stdin EOF was somehow never
//  delivered (e.g. a future stdin redirect, or a Windows handle-inheritance corner case).
private fun startParentWatchdog(parentPid: Long) {
    ProcessHandle.of(parentPid).ifPresent { parent ->
        parent.onExit().thenRun { exitProcess(0) }
    }
}


//---------------------------------------------------------------------------------------------------------------------
fun kzenAutoInit(args: Array<String>, jsModuleName: String, buildInfo: BuildInfo? = null): KzenAutoContext {
    // disable headless mode for Robot-based automation
    System.setProperty("java.awt.headless", "false")

    val port = KzenAutoConfig.readPort(args) ?: 8080

    val config = KzenAutoConfig(
        jsModuleName = jsModuleName,
        port = port,
        host = "127.0.0.1",
        moduleRoot = KzenAutoConfig.readModuleRoot(args),
        managedLifeline = KzenAutoConfig.readManagedLifeline(args),
        parentPid = KzenAutoConfig.readParentPid(args),
        buildInfo = buildInfo)

    val context = KzenAutoContext.create(config)

    Runtime.getRuntime().addShutdownHook(Thread {
        context.close()
    })

    // Bind this process's lifetime to the spawning parent's, so a shell-spawned child never outlives it
    //  (this is what prevents orphaned server JVMs). Wired here rather than in each main() so EVERY
    //  consumer of kzenAutoInit inherits it — notably KzenProjectMain, which reaches the server only
    //  through here. Gated on the managed flags, which only the shell passes; dev/standalone runs omit
    //  them and start no watchers. Registered after the shutdown hook so exitProcess runs context.close().
    if (config.managedLifeline) {
        startManagedLifeline()
    }
    config.parentPid?.let { startParentWatchdog(it) }

    return context
}


fun kzenAutoMain(context: KzenAutoContext) {
    embeddedServer(
        Netty,
        port = context.config.port,
        host = context.config.host
    ) {
        ktorMain(context)
    }.start(wait = true)
}


fun Application.ktorMain(
    context: KzenAutoContext
) {
    // SER5: kotlinx.serialization is the single structured-wire JSON codec (Jackson removed). This install
    // is here so a future call.respond(dto) over a @Serializable type resolves the kotlinx serializer; the
    // migrated handlers currently respond through respondJson (pre-encoded respondText — see its note below),
    // so the converter is not on the hot path today. serverJson is the stock Json config (see respondJson).
    install(ContentNegotiation) {
        json(serverJson)
    }

    // Server-Sent Events, for the /logic/events run-status push stream (see routeLogic). One-directional
    // server -> client is all a run status needs, and SSE rides the ordinary HTTP path — so it inherits the
    // kzen-shell proxy's prefixing and streaming relay unchanged.
    install(SSE)

    // gzip/deflate the JSON + text responses (trace/detached/notation). Base64-of-PNG screenshots in trace
    // JSON dominate the byte volume and compress heavily; this recovers most of base64's 33% tax and shrinks
    // the JSON envelope, helping all three progress stores (Script/Flow/Job). Pure transport encoding — no
    // wire-format change. The kzen-shell proxy relays it end-to-end unchanged: it forwards Accept-Encoding
    // upstream, forwards Content-Encoding back, and copies the body byte-for-byte (its CIO client installs no
    // ContentEncoding plugin, so it never decompresses). Two exclusions:
    //  - text/event-stream: the /logic/events SSE stream must never be buffered/compressed (would break
    //    incremental framing/flush and defeat the push design).
    //  - application/octet-stream: the resource route (respondBytes) serves already-compressed PNG bytes —
    //    gzip there is CPU for ~0 gain.
    // minimumSize keeps the tiny text/plain control-verb responses (logicStep*/logicCancel) uncompressed.
    install(Compression) {
        gzip()
        deflate()
        minimumSize(1024L)
        excludeContentType(ContentType.Text.EventStream)
        excludeContentType(ContentType.Application.OctetStream)
    }

    routing {
        routeRequests(context)
    }
}


//---------------------------------------------------------------------------------------------------------------------
private fun Routing.routeRequests(
    context: KzenAutoContext
) {
    get("/") {
        call.respondRedirect(indexFileName)
    }
    get(indexFilePath) {
        call.respondHtml(HttpStatusCode.OK) {
            indexPage(context.config)
        }
    }

    // Revalidate the SPA bundle (and all static assets) on every load so an upgraded build is picked
    //  up instead of a stale cached copy. If ever served over the internet / a CDN, switch to
    //  content-hashed immutable filenames (esbuild [hash]) to avoid the per-load revalidation.
    staticResources(staticResourcePath, staticResourceDir) {
        cacheControl { listOf(CacheControl.NoCache(null)) }
    }

    routeIcons()

    routeNotationQuery(context.notationQueryHandler)
    routeNotationCommands(context.notationCommandHandler)

    routeDetached(context.detachedActionHandler)
    routeTask(context.taskHandler)
    routeLogic(context.logicHandler)

    routeObjectStable(context.objectStableHandler)
    routeFileListing(context.fileListingHandler)
    routeStorage(context.storageHandler)
}


// Every structured JSON response goes through respondJson, which pre-encodes with kotlinx and hands the bytes
// to respondText. This is RETAINED (not transitional) on purpose: respondText yields a fully-buffered
// TextContent, which install(Compression) can gzip in place. Routing json(serverJson) through
// call.respond(dto) instead would let the converter emit a streaming WriteChannelContent — the exact case
// that forces Compression to buffer the whole body AND logs a per-response WARN ("Compressing a
// WriteChannelContent response ... defeats the purpose of streaming"). These JSON bodies are finite
// documents, so buffered encoding is the correct, warning-free path.
//
// serverJson is deliberately the STOCK config: encodeDefaults=false + explicitNulls=true means a nullable property
// WITH a `= null` default is omitted from the wire (StorageAreaInfo.budget, matching the legacy codec) while one
// WITHOUT a default encodes as an explicit JSON null — which is exactly what SER4's LogicStatus.active
// sentinel-kill needs. Do not set explicitNulls=false here.
private val serverJson = Json

private suspend inline fun <reified T> ApplicationCall.respondJson(dto: T) {
    respondText(serverJson.encodeToString(dto), ContentType.Application.Json)
}


private fun Routing.routeStorage(
    storageHandler: StorageHandler
) {
    get(CommonRestApi.storageSummary) {
        call.respondJson(storageHandler.storageSummary())
    }
    get(CommonRestApi.storageBundleList) {
        call.respondJson(storageHandler.storageBundleList(call.parameters))
    }
    get(CommonRestApi.storageBundleDelete) {
        // NB: text/plain error-message-or-empty, not JSON — deliberately un-migrated
        val response = storageHandler.storageBundleDelete(call.parameters)
        call.respond(response)
    }
}


private fun Routing.routeFileListing(
    fileListingHandler: FileListingHandler
) {
    get(CommonRestApi.fileListing) {
        call.respondJson(fileListingHandler.fileListing(call.parameters))
    }
}


private fun Routing.routeIcons() {
    // Iconify on-demand protocol: GET /icon/{set}.json?icons=name1,name2,... → IconifyJSON subset.
    // Tailcard capture (like routeNotationQuery) avoids mixed param+literal segment matching.
    get(CommonRestApi.iconCollectionPrefix + "{collection...}") {
        val collection = call.parameters.getAll("collection")?.joinToString("/") ?: ""
        val set = collection.removeSuffix(".json")
        val icons = call.request.queryParameters[CommonRestApi.paramIcons]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        call.response.header(HttpHeaders.CacheControl, "public, max-age=604800")
        call.respondText(IconCollectionHandler.query(set, icons), ContentType.Application.Json)
    }
}


private fun Routing.routeObjectStable(
    objectStableHandler: ObjectStableHandler
) {
    get(CommonRestApi.objectStableMapperSnapshot) {
        call.respondJson(objectStableHandler.objectStableMapperSnapshot())
    }
}


// How often an otherwise-idle /logic/events stream emits a keep-alive. Must stay comfortably BELOW the
// kzen-shell proxy's socket (inter-byte) timeout of 60s, or the proxy tears down an idle stream mid-life;
// 15s leaves 4x margin, tolerating three consecutive lost heartbeats. Also the client's liveness signal:
// its watchdog demotes to polling if nothing (heartbeat included) arrives for 45s.
private const val logicEventsHeartbeatMillis = 15_000L

// Floor on the interval between two pushes on one stream. The engine signals on every emit / log / park, so a
// hot run would otherwise serialize a status per event; at 100ms a stream costs at most ~10 status builds/s —
// still 15x faster than the 1.5s poll it replaces, and far under the 750ms slow-motion dwell. Signals arriving
// during the wait are not lost: the channel is CONFLATED, so the latest is retained and delivered next.
private const val logicEventsMinPushIntervalMillis = 100L


// A refused start answers 400 with the controller's reason as the body — the client shows it verbatim, so a
// compile failure is diagnosable at the browser instead of only in the server log.
private suspend fun ApplicationCall.respondLogicStart(attempt: LogicStartAttempt) {
    when (attempt) {
        is LogicStartAttempt.Started ->
            respondText(attempt.runId.value)

        is LogicStartAttempt.Failed ->
            respondText(attempt.reason, status = HttpStatusCode.BadRequest)
    }
}


private fun Routing.routeLogic(
    logicHandler: LogicHandler
) {
    // Push half of the run-status transport: the same payload logicStatus serves, sent as the run advances.
    // The client (ClientLogicGlobal) treats a pushed status identically to a polled one and keeps polling as an
    // adaptive fallback, so this endpoint failing — or being buffered by an intermediary — degrades to the
    // pre-push behaviour rather than freezing the UI.
    sse(CommonRestApi.logicEvents) {
        // CONFLATED: the engine's signal is payload-free and coalescing-safe, so an unread signal need only
        // record THAT something changed. Bounds memory under a hot run and lets the listener never block.
        val signals = Channel<Unit>(Channel.CONFLATED)

        // Cheap by contract: this runs on an engine dispatcher thread on the emit/log/park hot path, and
        // sometimes under the controller's monitor. trySend on a CONFLATED channel never suspends and never
        // re-enters the controller, so it can neither stall execution nor deadlock.
        val subscription = logicHandler.observeLogicStatus { signals.trySend(Unit) }

        try {
            // Send the current status immediately: it syncs a just-connected client, and doubles as the
            // client's delivery probe (a buffering intermediary opens the stream fine but delivers nothing,
            // so the client trusts only an ARRIVED message as proof the channel works).
            var lastSent = serverJson.encodeToString(logicHandler.logicStatus())
            send(ServerSentEvent(data = lastSent))

            while (true) {
                val signalled = withTimeoutOrNull(logicEventsHeartbeatMillis.milliseconds) { signals.receive() }

                if (signalled == null) {
                    // Idle. A named event, not a bare comment: a comment would keep the proxy socket alive but
                    // fire no EventSource event, leaving the client's watchdog blind to a dead-but-open socket.
                    // "ping" is invisible to onmessage, so it can't be mistaken for a status.
                    send(ServerSentEvent(event = "ping", data = ""))
                    continue
                }

                // Build the payload BEFORE suspending in send(): logicStatus() takes the controller's
                // monitor, and the lock must never be held across a suspension point (encoding runs after
                // status() has returned, so the monitor is already released here).
                val next = serverJson.encodeToString(logicHandler.logicStatus())

                // Signals are announced liberally (every accepted control verb, every engine change), and
                // several of them project to an identical status. Re-sending an identical payload would just
                // make the client re-derive the same version, so drop it here — which is what makes
                // over-announcing on the server side free.
                if (next != lastSent) {
                    lastSent = next
                    send(ServerSentEvent(data = next))
                }

                delay(logicEventsMinPushIntervalMillis.milliseconds)
            }
        }
        finally {
            // Mandatory: RunEngine.shutdown()/dispose() do not clear observer lists, and a browser tab may
            // close at any time — an unclosed subscription leaks for the life of the process.
            subscription.close()
        }
    }

    get(CommonRestApi.logicStatus) {
        call.respondJson(logicHandler.logicStatus())
    }
    get(CommonRestApi.logicStartAndRun) {
        call.respondLogicStart(
            logicHandler.logicStart(call.parameters, false))
    }
    // PUT twin: the client falls back to a form body when a long breakpoint list overflows the GET URL limit.
    put(CommonRestApi.logicStartAndRun) {
        val parameters = call.receiveParameters()
        call.respondLogicStart(
            logicHandler.logicStart(parameters, false))
    }
    get(CommonRestApi.logicStartAndStep) {
        call.respondLogicStart(
            logicHandler.logicStart(call.parameters, true))
    }
    put(CommonRestApi.logicStartAndStep) {
        val parameters = call.receiveParameters()
        call.respondLogicStart(
            logicHandler.logicStart(parameters, true))
    }
    get(CommonRestApi.logicRequest) {
        call.respondJson(logicHandler.logicRequest(call.parameters))
    }
    get(CommonRestApi.logicCancel) {
        val response = logicHandler.logicCancel(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.logicPause) {
        val response = logicHandler.logicPause(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.logicContinueRun) {
        val response = logicHandler.logicContinueRun(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.logicSetPauseOnError) {
        val response = logicHandler.logicSetPauseOnError(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.logicSetBreakpoints) {
        val response = logicHandler.logicSetBreakpoints(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.logicSetBreakpoints) {
        val parameters = call.receiveParameters()
        val response = logicHandler.logicSetBreakpoints(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.logicContinueStep) {
        val response = logicHandler.logicContinueStep(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.logicStepOver) {
        val response = logicHandler.logicStepOver(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.logicStepOut) {
        val response = logicHandler.logicStepOut(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.logicMoveTo) {
        val response = logicHandler.logicMoveTo(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.logicTraceBinary) {
        val bytes = logicHandler.logicTraceBinary(call.parameters)
        if (bytes == null) {
            call.respondText("Not found", status = HttpStatusCode.NotFound)
        }
        else {
            // A content hash never changes, so the browser may cache the blob indefinitely. Octet-stream rides
            // the Compression exclusion (PNG is already compressed) — see install(Compression) above.
            call.response.header(HttpHeaders.CacheControl, "public, immutable")
            call.respondBytes(bytes, ContentType.Application.OctetStream)
        }
    }
}


private fun Routing.routeTask(
    taskHandler: TaskHandler
) {
    get(CommonRestApi.taskSubmit) {
        call.respondJson(taskHandler.taskSubmit(call.parameters))
    }
    get(CommonRestApi.taskQuery) {
        val response = taskHandler.taskQuery(call.parameters)
        if (response == null) {
            call.respondText(
                "Task not found",
                status = HttpStatusCode.NotFound)
        }
        else {
            call.respondJson(response)
        }
    }
    get(CommonRestApi.taskCancel) {
        val response = taskHandler.taskCancel(call.parameters)
        if (response == null) {
            call.respondText(
                "Task not found",
                status = HttpStatusCode.NotFound)
        }
        else {
            call.respondJson(response)
        }
    }
    get(CommonRestApi.taskLookup) {
        call.respondJson(taskHandler.taskLookup(call.parameters))
    }
}


private fun Routing.routeNotationQuery(
    notationQueryHandler: NotationQueryHandler
) {
    get(CommonRestApi.scan) {
        call.respondJson(notationQueryHandler.scan(call.parameters))
    }
    get(CommonRestApi.notationPrefix + "{notationPath...}") {
        val notationPath = call.parameters.getAll("notationPath")?.joinToString("/") ?: ""
        val response = notationQueryHandler.notation(notationPath, false)
        call.respondText(response)
    }
    get(CommonRestApi.notationBatch) {
        call.respondJson(notationQueryHandler.notationBatch(call.parameters))
    }
    put(CommonRestApi.notationBatch) {
        val parameters = call.receiveParameters()
        call.respondJson(notationQueryHandler.notationBatch(parameters))
    }
    get(CommonRestApi.resource) {
        val response = notationQueryHandler.resourceRead(call.parameters)
        call.respondBytes(response)
    }
}


private fun Routing.routeNotationCommands(
    notationCommandHandler: NotationCommandHandler
) {
    get(CommonRestApi.commandDocumentCreate) {
        val response = notationCommandHandler.createDocument(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandDocumentDelete) {
        val response = notationCommandHandler.deleteDocument(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandDocumentSetObjects) {
        val response = notationCommandHandler.setDocumentObjects(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandDocumentSetObjects) {
        val parameters = call.receiveParameters()
        val response = notationCommandHandler.setDocumentObjects(parameters)
        call.respondText(response)
    }
    post(CommonRestApi.commandResourceAdd) {
        val bytes = call.receive<ByteArray>()
        val wrappedBytes = ImmutableByteArray.wrap(bytes)
        val response = notationCommandHandler.addResource(call.parameters, wrappedBytes)
        call.respondText(response)
    }
    get(CommonRestApi.commandResourceRemove) {
        val response = notationCommandHandler.resourceDelete(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectAdd) {
        val response = notationCommandHandler.addObject(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectRemove) {
        val response = notationCommandHandler.removeObject(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectShift) {
        val response = notationCommandHandler.shiftObject(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectShiftTree) {
        val response = notationCommandHandler.shiftObjectTree(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectRelocateTree) {
        val response = notationCommandHandler.relocateObjectTree(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectRename) {
        val response = notationCommandHandler.renameObject(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectAddAtAttribute) {
        val response = notationCommandHandler.addObjectAtAttribute(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectInsertInList) {
        val response = notationCommandHandler.insertObjectInList(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectRemoveIn) {
        val response = notationCommandHandler.removeObjectInAttribute(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeUpsert) {
        val response = notationCommandHandler.upsertAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeUpsert) {
        val parameters = call.receiveParameters()
        val response = notationCommandHandler.upsertAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeUpdateIn) {
        val response = notationCommandHandler.updateInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeUpdateIn) {
        val parameters = call.receiveParameters()
        val response = notationCommandHandler.updateInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeUpdateAllNestingsIn) {
        val response = notationCommandHandler.updateAllNestingsInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeUpdateAllNestingsIn) {
        val parameters = call.receiveParameters()
        val response = notationCommandHandler.updateAllNestingsInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeUpdateAllValuesIn) {
        val response = notationCommandHandler.updateAllValuesInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeUpdateAllValuesIn) {
        val parameters = call.receiveParameters()
        val response = notationCommandHandler.updateAllValuesInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeInsertItemIn) {
        val response = notationCommandHandler.insertListItemInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeInsertItemIn) {
        val parameters = call.receiveParameters()
        val response = notationCommandHandler.insertListItemInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeInsertAllItemsIn) {
        val response = notationCommandHandler.insertAllListItemsInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeInsertAllItemsIn) {
        val parameters = call.receiveParameters()
        val response = notationCommandHandler.insertAllListItemsInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeRemoveIn) {
        val response = notationCommandHandler.removeInAttribute(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeInsertEntryIn) {
        val response = notationCommandHandler.insertMapEntryInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeInsertEntryIn) {
        val parameters = call.receiveParameters()
        val response = notationCommandHandler.insertMapEntryInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeRemoveItemIn) {
        val response = notationCommandHandler.removeListItemInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeRemoveItemIn) {
        val parameters = call.receiveParameters()
        val response = notationCommandHandler.removeListItemInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeRemoveAllItemsIn) {
        val response = notationCommandHandler.removeAllListItemsInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeRemoveAllItemsIn) {
        val parameters = call.receiveParameters()
        val response = notationCommandHandler.removeAllListItemsInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeShiftIn) {
        val response = notationCommandHandler.shiftInAttribute(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandRefactorObjectRename) {
        val response = notationCommandHandler.refactorObjectName(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandRefactorDocumentRename) {
        val response = notationCommandHandler.refactorDocumentName(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandRefactorMove) {
        val response = notationCommandHandler.refactorMove(call.parameters)
        call.respondText(response)
    }
}


private fun Routing.routeDetached(
    detachedActionHandler: DetachedActionHandler
) {
    get(CommonRestApi.actionDetached) {
        call.respondJson(detachedActionHandler.actionDetached(call.parameters, null))
    }
    post(CommonRestApi.actionDetached) {
        val bytes = call.receiveNullable<ByteArray>()
        val wrappedBytes = bytes?.let { ImmutableByteArray.wrap(it) }
        call.respondJson(detachedActionHandler.actionDetached(call.parameters, wrappedBytes))
    }
    put(CommonRestApi.actionDetached) {
        if (call.request.isMultipart()) {
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                "Multipart PUT to actionDetached is not supported")
            return@put
        }

        val formParameters = call.receiveParameters()
        call.respondJson(detachedActionHandler.actionDetached(formParameters, null))
    }
    get(CommonRestApi.actionDetachedDownload) {
        val bytes = call.receiveNullable<ByteArray>()
        val wrappedBytes = bytes?.let { ImmutableByteArray.wrap(it) }
        val response = detachedActionHandler.actionDetachedDownload(call.parameters, wrappedBytes)

        val attachmentFilename = "attachment; filename*=utf-8''" + response.fileName
        call.response.header(HttpHeaders.ContentDisposition, attachmentFilename)

        call.respondOutputStream(
            ContentType.parse(response.mimeType)
        ) {
            ByteStreams.copy(response.data, this)
        }
    }
    // Streams a Job Explore Worker's persisted table.csv (resolved from notation, no live run needed) — the
    // same attachment plumbing as actionDetachedDownload above; see DetachedActionHandler.jobDownload.
    get(CommonRestApi.jobDownload) {
        val response = detachedActionHandler.jobDownload(call.parameters)

        val attachmentFilename = "attachment; filename*=utf-8''" + response.fileName
        call.response.header(HttpHeaders.ContentDisposition, attachmentFilename)

        call.respondOutputStream(
            ContentType.parse(response.mimeType)
        ) {
            ByteStreams.copy(response.data, this)
        }
    }
}


