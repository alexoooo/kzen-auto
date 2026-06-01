package tech.kzen.auto.server

import com.google.common.io.ByteStreams
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.api.staticResourceDir
import tech.kzen.auto.common.api.staticResourcePath
import tech.kzen.auto.server.api.IconCollectionHandler
import tech.kzen.auto.server.api.RestHandler
import tech.kzen.auto.server.backend.indexPage
import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.lib.common.util.ImmutableByteArray


//---------------------------------------------------------------------------------------------------------------------
const val kzenAutoJsModuleName = "kzen-auto-js"
//const val jsResourcePath = "$staticResourcePath/$jsFileName"

private const val indexFileName = "index.html"
private const val indexFilePath = "/$indexFileName"


//---------------------------------------------------------------------------------------------------------------------
fun main(args: Array<String>) {
    val context = kzenAutoInit(args, kzenAutoJsModuleName)
    kzenAutoMain(context)
}


//---------------------------------------------------------------------------------------------------------------------
fun kzenAutoInit(args: Array<String>, jsModuleName: String): KzenAutoContext {
    // disable headless mode for Robot-based automation
    System.setProperty("java.awt.headless", "false")

    val port = KzenAutoConfig.readPort(args) ?: 8080

    val config = KzenAutoConfig(
        jsModuleName = jsModuleName,
        port = port,
        host = "127.0.0.1")

    val context = KzenAutoContext(config)

    context.init()

    Runtime.getRuntime().addShutdownHook(Thread {
        context.close()
    })

    KzenAutoContext.setGlobal(context)

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
    install(ContentNegotiation) {
        jackson()
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

    staticResources(staticResourcePath, staticResourceDir)

    routeIcons()

    routeNotationQuery(context.restHandler)
    routeNotationCommands(context.restHandler)

    routeDetached(context.restHandler)
    routeTask(context.restHandler)
    routeLogic(context.restHandler)

    routeDataflow(context.restHandler)

    routeObjectStable(context.restHandler)
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

        // Icon data is a build artifact (the bundled material-symbols collection) — immutable for the life
        // of this build, and a given name's glyph never changes within a published collection. Without an
        // explicit lifetime the browser revalidates on every reload, and since Iconify caches icons only in
        // memory (no localStorage layer in @iconify/react), every page reload re-fetches every glyph. A long
        // max-age lets the browser serve identical batches from its own cache, so each batch is downloaded at
        // most once per window; an icon-set dependency bump self-heals once the window lapses.
        call.response.header(HttpHeaders.CacheControl, "public, max-age=604800")
        call.respondText(IconCollectionHandler.query(set, icons), ContentType.Application.Json)
    }
}


private fun Routing.routeObjectStable(
    restHandler: RestHandler
) {
    get(CommonRestApi.objectStableMapperSnapshot) {
        val response = restHandler.objectStableMapperSnapshot()
        call.respond(response)
    }
}


private fun Routing.routeLogic(
    restHandler: RestHandler
) {
    get(CommonRestApi.logicStatus) {
        val response = restHandler.logicStatus()
        call.respond(response)
    }
    get(CommonRestApi.logicStartAndRun) {
        val response = restHandler.logicStart(call.parameters, false)
        if (response == null) {
            call.respondText(
                "Unable to start logic run",
                status = HttpStatusCode.BadRequest)
        }
        else {
            call.respondText(response)
        }
    }
    get(CommonRestApi.logicStartAndStep) {
        val response = restHandler.logicStart(call.parameters, true)
        if (response == null) {
            call.respondText(
                "Unable to start logic run",
                status = HttpStatusCode.BadRequest)
        }
        else {
            call.respondText(response)
        }
    }
    get(CommonRestApi.logicRequest) {
        val response = restHandler.logicRequest(call.parameters)
        call.respond(response)
    }
    get(CommonRestApi.logicCancel) {
        val response = restHandler.logicCancel(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.logicPause) {
        val response = restHandler.logicPause(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.logicContinueRun) {
        val response = restHandler.logicContinueRun(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.logicContinueStep) {
        val response = restHandler.logicContinueStep(call.parameters)
        call.respondText(response)
    }
}


private fun Routing.routeTask(
    restHandler: RestHandler
) {
    get(CommonRestApi.taskSubmit) {
        val response = restHandler.taskSubmit(call.parameters)
        call.respond(response)
    }
    get(CommonRestApi.taskQuery) {
        val response = restHandler.taskQuery(call.parameters)
        if (response == null) {
            call.respondText(
                "Task not found",
                status = HttpStatusCode.NotFound)
        }
        else {
            call.respond(response)
        }
    }
    get(CommonRestApi.taskCancel) {
        val response = restHandler.taskCancel(call.parameters)
        if (response == null) {
            call.respondText(
                "Task not found",
                status = HttpStatusCode.NotFound)
        }
        else {
            call.respond(response)
        }
    }
    get(CommonRestApi.taskLookup) {
        val response = restHandler.taskLookup(call.parameters)
        call.respond(response)
    }
}


private fun Routing.routeDataflow(
    restHandler: RestHandler
) {
    get(CommonRestApi.dataflowModel) {
        val response = restHandler.dataflowModel(call.parameters)
        call.respond(response)
    }
    get(CommonRestApi.dataflowReset) {
        val response = restHandler.dataflowReset(call.parameters)
        call.respond(response)
    }
    get(CommonRestApi.dataflowPerform) {
        val response = restHandler.dataflowPerform(call.parameters)
        call.respond(response)
    }
}


private fun Routing.routeNotationQuery(
    restHandler: RestHandler
) {
    get(CommonRestApi.scan) {
        val response = restHandler.scan(call.parameters)
        call.respond(response)
    }
    get(CommonRestApi.notationPrefix + "{notationPath...}") {
        val notationPath = call.parameters.getAll("notationPath")?.joinToString("/") ?: ""
        val response = restHandler.notation(notationPath, false)
        call.respondText(response)
    }
    get(CommonRestApi.notationBatch) {
        val response = restHandler.notationBatch(call.parameters)
        call.respond(response)
    }
    put(CommonRestApi.notationBatch) {
        val parameters = call.receiveParameters()
        val response = restHandler.notationBatch(parameters)
        call.respond(response)
    }
    get(CommonRestApi.resource) {
        val response = restHandler.resourceRead(call.parameters)
        call.respondBytes(response)
    }
}


private fun Routing.routeNotationCommands(
    restHandler: RestHandler
) {
    get(CommonRestApi.commandDocumentCreate) {
        val response = restHandler.createDocument(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandDocumentDelete) {
        val response = restHandler.deleteDocument(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandDocumentSetObjects) {
        val response = restHandler.setDocumentObjects(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandDocumentSetObjects) {
        val parameters = call.receiveParameters()
        val response = restHandler.setDocumentObjects(parameters)
        call.respondText(response)
    }
    post(CommonRestApi.commandResourceAdd) {
        val bytes = call.receive<ByteArray>()
        val wrappedBytes = ImmutableByteArray.wrap(bytes)
        val response = restHandler.addResource(call.parameters, wrappedBytes)
        call.respondText(response)
    }
    get(CommonRestApi.commandResourceRemove) {
        val response = restHandler.resourceDelete(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectAdd) {
        val response = restHandler.addObject(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectRemove) {
        val response = restHandler.removeObject(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectShift) {
        val response = restHandler.shiftObject(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectRename) {
        val response = restHandler.renameObject(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectAddAtAttribute) {
        val response = restHandler.addObjectAtAttribute(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectInsertInList) {
        val response = restHandler.insertObjectInList(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandObjectRemoveIn) {
        val response = restHandler.removeObjectInAttribute(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeUpsert) {
        val response = restHandler.upsertAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeUpsert) {
        val parameters = call.receiveParameters()
        val response = restHandler.upsertAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeUpdateIn) {
        val response = restHandler.updateInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeUpdateIn) {
        val parameters = call.receiveParameters()
        val response = restHandler.updateInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeUpdateAllNestingsIn) {
        val response = restHandler.updateAllNestingsInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeUpdateAllNestingsIn) {
        val parameters = call.receiveParameters()
        val response = restHandler.updateAllNestingsInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeUpdateAllValuesIn) {
        val response = restHandler.updateAllValuesInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeUpdateAllValuesIn) {
        val parameters = call.receiveParameters()
        val response = restHandler.updateAllValuesInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeInsertItemIn) {
        val response = restHandler.insertListItemInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeInsertItemIn) {
        val parameters = call.receiveParameters()
        val response = restHandler.insertListItemInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeInsertAllItemsIn) {
        val response = restHandler.insertAllListItemsInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeInsertAllItemsIn) {
        val parameters = call.receiveParameters()
        val response = restHandler.insertAllListItemsInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeRemoveIn) {
        val response = restHandler.removeInAttribute(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeInsertEntryIn) {
        val response = restHandler.insertMapEntryInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeInsertEntryIn) {
        val parameters = call.receiveParameters()
        val response = restHandler.insertMapEntryInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeRemoveItemIn) {
        val response = restHandler.removeListItemInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeRemoveItemIn) {
        val parameters = call.receiveParameters()
        val response = restHandler.removeListItemInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeRemoveAllItemsIn) {
        val response = restHandler.removeAllListItemsInAttribute(call.parameters)
        call.respondText(response)
    }
    put(CommonRestApi.commandAttributeRemoveAllItemsIn) {
        val parameters = call.receiveParameters()
        val response = restHandler.removeAllListItemsInAttribute(parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandAttributeShiftIn) {
        val response = restHandler.shiftInAttribute(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandRefactorObjectRename) {
        val response = restHandler.refactorObjectName(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.commandRefactorDocumentRename) {
        val response = restHandler.refactorDocumentName(call.parameters)
        call.respondText(response)
    }
}


private fun Routing.routeDetached(
    restHandler: RestHandler
) {
    get(CommonRestApi.actionDetached) {
        val response = restHandler.actionDetached(call.parameters, null)
        call.respond(response)
    }
    post(CommonRestApi.actionDetached) {
        val bytes = call.receiveNullable<ByteArray>()
        val wrappedBytes = bytes?.let { ImmutableByteArray.wrap(it) }
        val response = restHandler.actionDetached(call.parameters, wrappedBytes)
        call.respond(response)
    }
    put(CommonRestApi.actionDetached) {
        if (call.request.isMultipart()) {
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                "Multipart PUT to actionDetached is not supported")
            return@put
        }

        val formParameters = call.receiveParameters()
        val response = restHandler.actionDetached(formParameters, null)
        call.respond(response)
    }
    get(CommonRestApi.actionDetachedDownload) {
        val bytes = call.receiveNullable<ByteArray>()
        val wrappedBytes = bytes?.let { ImmutableByteArray.wrap(it) }
        val response = restHandler.actionDetachedDownload(call.parameters, wrappedBytes)

        val attachmentFilename = "attachment; filename*=utf-8''" + response.fileName
        call.response.header(HttpHeaders.ContentDisposition, attachmentFilename)

        call.respondOutputStream(
            ContentType.parse(response.mimeType)
        ) {
            ByteStreams.copy(response.data, this)
        }
    }
}


