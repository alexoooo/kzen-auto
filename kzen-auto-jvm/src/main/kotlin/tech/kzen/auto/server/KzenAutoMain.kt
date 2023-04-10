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
import kotlinx.html.HTML
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.api.staticResourceDir
import tech.kzen.auto.common.api.staticResourcePath
import tech.kzen.auto.server.api.RestHandler
import tech.kzen.auto.server.backend.indexPage
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.lib.common.util.ImmutableByteArray


const val jsFileName = "kzen-auto-js.js"
const val jsResourcePath = "$staticResourcePath/$jsFileName"

private const val indexFileName = "index.html"
private const val indexFilePath = "/$indexFileName"


fun main() {
    kzenAutoInit()
    runEmbeddedServer()
}


fun kzenAutoInit() {
    // NB: disable headless mode
    // https://stackoverflow.com/questions/40016683/spring-boot-forcing-headless-mode
    System.setProperty("java.awt.headless", "false")
}


private fun runEmbeddedServer() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "127.0.0.1"
    ) {
        ktorMain()
    }.start(wait = true)
}


fun Application.ktorMain() {
//    println("developmentMode: " + environment.developmentMode)

    install(ContentNegotiation) {
        jackson()
    }

    ServerContext.init()
    try {
        val restHandler = RestHandler()
        routing {
            routeRequests(restHandler)
        }
    }
    finally {
        ServerContext.close()
    }
}


private fun Routing.routeRequests(
    restHandler: RestHandler
) {
    get("/") {
//        call.respondRedirect("index.html")
        call.respondRedirect(indexFileName)
    }
    get(indexFilePath) {
        call.respondHtml(HttpStatusCode.OK, HTML::indexPage)
//        call.respondHtml(HttpStatusCode.OK) {
//            Pages.index()
//        }
    }

    static(staticResourcePath) {
        resources(staticResourceDir)
    }

    routeNotationQuery(restHandler)
    routeNotationCommands(restHandler)

    routeDetached(restHandler)
    routeTask(restHandler)
    routeLogic(restHandler)

    routeScript(restHandler)
    routeDataflow(restHandler)
}


private fun Routing.routeLogic(
    restHandler: RestHandler
) {
    get(CommonRestApi.logicStatus) {
        val response = restHandler.logicStatus()
        call.respond(response)
    }
    get(CommonRestApi.logicStart) {
        val response = restHandler.logicStart(call.parameters)
        if (response == null) {
            call.response.status(HttpStatusCode.BadRequest)
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
    get(CommonRestApi.logicRun) {
        val response = restHandler.logicRun(call.parameters)
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
            call.response.status(HttpStatusCode.NoContent)
        }
        else {
            call.respond(response)
        }
    }
    get(CommonRestApi.taskCancel) {
        val response = restHandler.taskCancel(call.parameters)
        if (response == null) {
            call.response.status(HttpStatusCode.NoContent)
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
    get(CommonRestApi.execModel) {
        val response = restHandler.execModel(call.parameters)
        call.respond(response)
    }
    get(CommonRestApi.execReset) {
        val response = restHandler.execReset(call.parameters)
        call.respond(response)
    }
    get(CommonRestApi.execPerform) {
        val response = restHandler.execPerform(call.parameters)
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
        val bytes = call.receiveNullable<ByteArray>()
        val wrappedBytes = bytes?.let { ImmutableByteArray.wrap(it) }
        val formParameters = call.receiveParameters()
        val response = restHandler.actionDetached(formParameters, wrappedBytes)
        call.respond(response)
    }
    get(CommonRestApi.actionDetachedDownload) {
        val bytes = call.receiveNullable<ByteArray>()
        val wrappedBytes = bytes?.let { ImmutableByteArray.wrap(it) }
        val response = restHandler.actionDetachedDownload(call.parameters, wrappedBytes)

        val attachmentFilename = "attachment; filename*=utf-8''" + response.fileName
        call.response.header("Content-Disposition", attachmentFilename)

        call.respondOutputStream(
            ContentType.parse(response.mimeType)
        ) {
            ByteStreams.copy(response.data, this)
        }
    }
}


private fun Routing.routeScript(
    restHandler: RestHandler
) {
    get(CommonRestApi.actionList) {
        val response = restHandler.actionList()
        call.respond(response)
    }
    get(CommonRestApi.actionModel) {
        val response = restHandler.actionModel(call.parameters)
        call.respond(response)
    }
    get(CommonRestApi.actionStart) {
        val response = restHandler.actionStart(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.actionReturn) {
        val response = restHandler.actionReturn(call.parameters)
        call.respondText(response)
    }
    get(CommonRestApi.actionReset) {
        restHandler.actionReset(call.parameters)
        call.response.status(HttpStatusCode.OK)
    }
    get(CommonRestApi.actionPerform) {
        val response = restHandler.actionPerform(call.parameters)
        call.respond(response)
    }
}

