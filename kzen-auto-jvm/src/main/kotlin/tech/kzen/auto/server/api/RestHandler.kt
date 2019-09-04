package tech.kzen.auto.server.api

import com.google.common.io.MoreFiles
import com.google.common.io.Resources
import kotlinx.coroutines.runBlocking
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResponse
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.structure.notation.edit.*
import tech.kzen.lib.common.structure.notation.model.AttributeNotation
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.ObjectNotation
import tech.kzen.lib.common.structure.notation.model.PositionIndex
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.IoUtils
import tech.kzen.lib.platform.collect.persistentListOf
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors


@Component
class RestHandler {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val classPathRoots = listOf(
                URI("classpath:/public/"))

        val resourceDirectories = discoverResourceDirectories()

        private const val cssExtension = "css"

        val allowedExtensions = listOf(
                "html",
                "js",
                cssExtension,
                "svg",
                "png",
                "ico")

        private val cssMediaType = MediaType.valueOf("text/css")


        private const val jvmSuffix = "-jvm"

        private fun discoverResourceDirectories(): List<Path> {
            val builder = mutableListOf<Path>()

            // TODO: consolidate with GradleLocator?

            val projectRoot =
                    if (Files.exists(Paths.get("src"))) {
                        ".."
                    }
                    else {
                        "."
                    }

            val projectName: String? = Files.list(Paths.get(projectRoot)).use { files ->
                val list = files.collect(Collectors.toList())

                val jvmModule = list.firstOrNull { it.fileName.toString().endsWith(jvmSuffix)}
                if (jvmModule == null) {
                    null
                }
                else {
                    val filename = jvmModule.fileName.toString()

                    filename.substring(0 until filename.length - jvmSuffix.length)
                }
            }

            if (projectName != null) {
                // IntelliJ and typical commandline working dir is project root
                builder.add(Paths.get("$projectName-jvm/src/main/resources/public/"))
                builder.add(Paths.get("$projectName-js/build/dist/"))

                // Eclipse and Gradle default active working directory is the module
                builder.add(Paths.get("src/main/resources/public/"))
                builder.add(Paths.get("../$projectName-js/build/dist/"))
            }
            else {
                builder.add(Paths.get("static/"))
            }

            return builder
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun scan(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentTree = runBlocking {
            ServerContext.notationMedia.scan()
        }

        val asMap = mutableMapOf<String, String>()

        for (e in documentTree.documents.values) {
            asMap[e.key.asRelativeFile()] = e.value.documentDigest.asString()
        }

        return ServerResponse
                .ok()
                .body(Mono.just(asMap))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun notation(serverRequest: ServerRequest): Mono<ServerResponse> {
        val encodedRequestSuffix = serverRequest.path().substring(CommonRestApi.notationPrefix.length)
        val requestSuffix = URI(encodedRequestSuffix).path

        val notationPath = DocumentPath.parse(requestSuffix)
        val notationBytes = runBlocking {
            ServerContext.notationMedia.read(notationPath)
        }

        val notationText = IoUtils.utf8Decode(notationBytes)

        val responseBuilder = ServerResponse.ok()
        return if (notationText.isEmpty()) {
            responseBuilder.build()
        }
        else {
            responseBuilder.body(Mono.just(notationText))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun createDocument(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val documentBody: DocumentNotation = serverRequest.getParam(CommonRestApi.paramDocumentNotation) {
            DocumentNotation(
                    ServerContext.yamlParser.parseDocumentObjects(IoUtils.utf8Encode(it)),
                    null)
        }

        return applyAndDigest(
                CreateDocumentCommand(documentPath, documentBody))
    }


    fun deleteDocument(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        return applyAndDigest(
                DeleteDocumentCommand(documentPath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun addObject(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val indexInDocument: PositionIndex = serverRequest.getParam(
                CommonRestApi.paramPositionIndex, PositionIndex.Companion::parse)

        val objectNotation: ObjectNotation = serverRequest.getParam(
                CommonRestApi.paramObjectNotation, ServerContext.yamlParser::parseObject)

        return applyAndDigest(
                AddObjectCommand(
                        ObjectLocation(documentPath, objectPath),
                        indexInDocument,
                        objectNotation))
    }


    fun removeObject(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        return applyAndDigest(
                RemoveObjectCommand(
                        ObjectLocation(documentPath, objectPath)))
    }


    fun shiftObject(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val indexInDocument: PositionIndex = serverRequest.getParam(
                CommonRestApi.paramPositionIndex, PositionIndex.Companion::parse)

        return applyAndDigest(
                ShiftObjectCommand(
                        ObjectLocation(documentPath, objectPath),
                        indexInDocument))
    }


    fun renameObject(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val objectName: ObjectName = serverRequest.getParam(
                CommonRestApi.paramObjectName, ::ObjectName)

        return applyAndDigest(
                RenameObjectCommand(
                        ObjectLocation(documentPath, objectPath),
                        objectName))
    }


    fun insertObjectInList(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val containingObjectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val containingList: AttributePath = serverRequest.getParam(
                CommonRestApi.paramAttributePath, AttributePath.Companion::parse)

        val indexInList: PositionIndex = serverRequest.getParam(
                CommonRestApi.paramPositionIndex, PositionIndex.Companion::parse)

        val objectName: ObjectName = serverRequest.getParam(
                CommonRestApi.paramObjectName, ::ObjectName)

        val positionInDocument: PositionIndex = serverRequest.getParam(
                CommonRestApi.paramSecondaryPosition, PositionIndex.Companion::parse)

        val objectNotation: ObjectNotation = serverRequest.getParam(
                CommonRestApi.paramObjectNotation, ServerContext.yamlParser::parseObject)

        return applyAndDigest(
                InsertObjectInListAttributeCommand(
                        ObjectLocation(documentPath, containingObjectPath),
                        containingList,
                        indexInList,
                        objectName,
                        positionInDocument,
                        objectNotation))
    }


    fun removeObjectInAttribute(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val containingObjectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val attributePath: AttributePath = serverRequest.getParam(
                CommonRestApi.paramAttributePath, AttributePath.Companion::parse)

        return applyAndDigest(
                RemoveObjectInAttributeCommand(
                        ObjectLocation(documentPath, containingObjectPath),
                        attributePath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun upsertAttribute(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val attributeName: AttributeName = serverRequest.getParam(
                CommonRestApi.paramAttributeName, ::AttributeName)

        val attributeNotation: AttributeNotation = serverRequest.getParam(
                CommonRestApi.paramAttributeNotation, ServerContext.yamlParser::parseAttribute)

        return applyAndDigest(
                UpsertAttributeCommand(
                        ObjectLocation(documentPath, objectPath),
                        attributeName,
                        attributeNotation))
    }


    fun updateInAttribute(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val attributePath: AttributePath = serverRequest.getParam(
                CommonRestApi.paramAttributePath, AttributePath.Companion::parse)

        val attributeNotation: AttributeNotation = serverRequest.getParam(
                CommonRestApi.paramAttributeNotation, ServerContext.yamlParser::parseAttribute)

        return applyAndDigest(
                UpdateInAttributeCommand(
                        ObjectLocation(documentPath, objectPath),
                        attributePath,
                        attributeNotation))
    }


    fun insertListItemInAttribute(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val containingList: AttributePath = serverRequest.getParam(
                CommonRestApi.paramAttributePath, AttributePath.Companion::parse)

        val indexInList: PositionIndex = serverRequest.getParam(
                CommonRestApi.paramPositionIndex, PositionIndex.Companion::parse)

        val itemNotation: AttributeNotation = serverRequest.getParam(
                CommonRestApi.paramAttributeNotation, ServerContext.yamlParser::parseAttribute)

        return applyAndDigest(
                InsertListItemInAttributeCommand(
                        ObjectLocation(documentPath, objectPath),
                        containingList,
                        indexInList,
                        itemNotation))
    }


    fun insertMapEntryInAttribute(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val containingMap: AttributePath = serverRequest.getParam(
                CommonRestApi.paramAttributePath, AttributePath.Companion::parse)

        val indexInMap: PositionIndex = serverRequest.getParam(
                CommonRestApi.paramPositionIndex, PositionIndex.Companion::parse)

        val mapKey: AttributeSegment = serverRequest.getParam(
                CommonRestApi.paramAttributeKey, AttributeSegment.Companion::parse)

        val valueNotation: AttributeNotation = serverRequest.getParam(
                CommonRestApi.paramAttributeNotation, ServerContext.yamlParser::parseAttribute)

        return applyAndDigest(
                InsertMapEntryInAttributeCommand(
                        ObjectLocation(documentPath, objectPath),
                        containingMap,
                        indexInMap,
                        mapKey,
                        valueNotation))
    }


    fun removeInAttribute(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val attributePath: AttributePath = serverRequest.getParam(
                CommonRestApi.paramAttributePath, AttributePath.Companion::parse)

        return applyAndDigest(
                RemoveInAttributeCommand(
                        ObjectLocation(documentPath, objectPath),
                        attributePath))
    }


    fun shiftInAttribute(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val attributePath: AttributePath = serverRequest.getParam(
                CommonRestApi.paramAttributePath, AttributePath.Companion::parse)

        val newPosition: PositionIndex = serverRequest.getParam(
                CommonRestApi.paramPositionIndex, PositionIndex.Companion::parse)

        return applyAndDigest(
                ShiftInAttributeCommand(
                        ObjectLocation(documentPath, objectPath),
                        attributePath,
                        newPosition))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun refactorObjectName(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val newName: ObjectName = serverRequest.getParam(
                CommonRestApi.paramObjectName, ::ObjectName)

        return applyAndDigest(
                RenameObjectRefactorCommand(
                        ObjectLocation(documentPath, objectPath),
                        newName))
    }


    fun refactorDocumentName(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val newName: DocumentName = serverRequest.getParam(
                CommonRestApi.paramDocumentName, ::DocumentName)

        return applyAndDigest(
                RenameDocumentRefactorCommand(
                        documentPath,
                        newName))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun applyAndDigest(command: NotationCommand): Mono<ServerResponse> {
        val digest = applyCommand(command)

        return ServerResponse
                .ok()
                .body(Mono.just(digest.asString()))
    }


    fun applyCommand(command: NotationCommand): Digest {
        return runBlocking {
            val event = ServerContext.repository.apply(command)

            // TODO: consolidate with CommandBus?
            ServerContext.graphStructureManager.onEvent(event)

            ServerContext.repository.digest()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun actionModel(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val executionModel = runBlocking {
            ServerContext.executionManager.executionModel(documentPath)
        }

        return ServerResponse
                .ok()
                .body(Mono.just(ImperativeModel.toCollection(executionModel)))
    }


    fun actionStart(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val digest = runBlocking {
            val graphStructure = ServerContext.graphStructureManager.serverGraphStructure()

            ServerContext.executionManager.start(
                    documentPath, graphStructure)
        }

        return ServerResponse
                .ok()
                .body(Mono.just(digest.asString()))
    }


    fun actionReset(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val digest = runBlocking {
            ServerContext.executionManager.reset(documentPath)
        }

        return ServerResponse
                .ok()
                .body(Mono.just(digest.asString()))
    }


    fun actionPerform(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val execution: ImperativeResponse = runBlocking {
            ServerContext.executionManager.execute(documentPath, objectLocation)
        }

        return ServerResponse
                .ok()
                .body(Mono.just(execution.toCollection()))
    }


    fun actionDetached(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val execution: ImperativeResult = runBlocking {
            ServerContext.actionExecutor.execute(
                    objectLocation,
                    ImperativeModel(persistentListOf()))
        }

        return ServerResponse
                .ok()
                .body(Mono.just(execution.toCollection()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun execModel(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath? = serverRequest.tryGetParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val visualDataflowModel = runBlocking {
            ServerContext.visualDataflowManager.get(documentPath)
        }

        val result =
                if (objectPath == null) {
                    VisualDataflowModel.toCollection(visualDataflowModel)
                }
                else {
                    val objectLocation = ObjectLocation(documentPath, objectPath)
                    val visualVertexModel = visualDataflowModel.vertices[objectLocation]
                            ?: throw IllegalArgumentException("Object location not found: $objectLocation")

                    VisualVertexModel.toCollection(visualVertexModel)
                }

        return ServerResponse
                .ok()
                .body(Mono.just(result))
    }


    fun execReset(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val visualDataflowModel = runBlocking {
            ServerContext.visualDataflowManager.reset(documentPath)
        }

        return ServerResponse
                .ok()
                .body(Mono.just(VisualDataflowModel.toCollection(visualDataflowModel)))
    }


    fun execPerform(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val transition: VisualVertexTransition = runBlocking {
            ServerContext.visualDataflowManager.execute(documentPath, objectLocation)
        }

        return ServerResponse
                .ok()
                .body(Mono.just(VisualVertexTransition.toCollection(transition)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // TODO: is this secure?
    fun resource(serverRequest: ServerRequest): Mono<ServerResponse> {
        val excludingInitialSlash = serverRequest.path().substring(1)

        val resolvedPath =
                if (excludingInitialSlash == "") {
                    "index.html"
                }
                else {
                    excludingInitialSlash
                }

        val path = Paths.get(resolvedPath).normalize()
        val extension = MoreFiles.getFileExtension(path)

        if (! isResourceAllowed(path)) {
            return ServerResponse
                    .badRequest()
                    .build()
        }

        val bytes: ByteArray = readResource(path)
                ?: return ServerResponse
                        .notFound()
                        .build()

        val responseBuilder = ServerResponse.ok()

        val responseType: MediaType? = responseType(extension)
        if (responseType !== null) {
            responseBuilder.contentType(responseType)
        }

        return if (bytes.isEmpty()) {
            responseBuilder.build()
        }
        else {
            responseBuilder.body(Mono.just(bytes))
        }
    }


    private fun responseType(extension: String): MediaType? {
        return when (extension) {
            cssExtension -> cssMediaType

            else -> null
        }
    }


    private fun isResourceAllowed(path: Path): Boolean {
        if (path.isAbsolute) {
            return false
        }

        val extension = MoreFiles.getFileExtension(path)
        return allowedExtensions.contains(extension)
    }


    private fun readResource(relativePath: Path): ByteArray? {
        // NB: moving up to help with auto-reload of js, TODO: this used to work below classPathRoots
        for (root in resourceDirectories) {
            val candidate = root.resolve(relativePath)
            if (! Files.exists(candidate)) {
                continue
            }

            return Files.readAllBytes(candidate)
        }

        for (root in classPathRoots) {
            try {
                val resourceLocation: URI = root.resolve(relativePath.toString())
                val relativeResource = resourceLocation.path.substring(1)
                val resourceUrl = Resources.getResource(relativeResource)
                return Resources.toByteArray(resourceUrl)
            }
            catch (ignored: Exception) {}
        }

//        println("%%%%% not read: $relativePath")
        return null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun <T> ServerRequest.getParam(
            parameterName: String,
            parser: (String) -> T
    ): T {
        return queryParam(parameterName)
                .map { parser(it) }
                .orElseThrow { IllegalArgumentException("'$parameterName' required") }
    }


    private fun <T> ServerRequest.tryGetParam(
            parameterName: String,
            parser: (String) -> T
    ): T? {
        return queryParam(parameterName)
                .map { parser(it) }
                .orElse(null)
    }
}