package tech.kzen.auto.server.api

import com.google.common.io.MoreFiles
import com.google.common.io.Resources
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionModel
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionResponse
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.structure.notation.edit.*
import tech.kzen.lib.common.structure.notation.model.AttributeNotation
import tech.kzen.lib.common.structure.notation.model.ObjectNotation
import tech.kzen.lib.common.structure.notation.model.PositionIndex
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.IoUtils
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

        val allowedExtensions = listOf(
                "html",
                "js",
                "css",
                "svg",
                "png",
                "ico")


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

        for (e in documentTree.values) {
            asMap[e.key.asRelativeFile()] = e.value.asString()
        }

        return ServerResponse
                .ok()
                .body(Mono.just(asMap))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun notation(serverRequest: ServerRequest): Mono<ServerResponse> {
        val notationPrefix = "/notation/"
        val requestSuffix = serverRequest.path().substring(notationPrefix.length)

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

        return applyAndDigest(
                CreateDocumentCommand(documentPath))
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
    fun refactorName(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
                CommonRestApi.paramObjectPath, ObjectPath.Companion::parse)

        val newName: ObjectName = serverRequest.getParam(
                CommonRestApi.paramObjectName, ::ObjectName)

        return applyAndDigest(
                RenameRefactorCommand(
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
            ServerContext.modelManager.onEvent(event)

            ServerContext.repository.digest()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun actionModel(serverRequest: ServerRequest): Mono<ServerResponse> {
        val executionModel = runBlocking {
            ServerContext.executionManager.executionModel()
        }

        return ServerResponse
                .ok()
                .body(Mono.just(ExecutionModel.toCollection(executionModel)))
    }


    fun actionStart(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val digest = runBlocking {
            val projectModel = ServerContext.modelManager.graphStructure()

            ServerContext.executionManager.start(
                    documentPath, projectModel)
        }

        return ServerResponse
                .ok()
                .body(Mono.just(digest.asString()))
    }


    fun actionReset(serverRequest: ServerRequest): Mono<ServerResponse> {
        val digest = runBlocking {
            ServerContext.executionManager.reset()
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

        val execution: ExecutionResponse = runBlocking {
            ServerContext.executionManager.execute(objectLocation)
        }

        return ServerResponse
                .ok()
                .body(Mono.just(execution.toCollection()))
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
        return if (bytes.isEmpty()) {
            responseBuilder.build()
        }
        else {
            responseBuilder.body(Mono.just(bytes))
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
        for (root in classPathRoots) {
            try {
                val resourceLocation: URI = root.resolve(relativePath.toString())
                val relativeResource = resourceLocation.path.substring(1)

//                println("%%%%% looking at resource: $relativeResource")
                val resourceUrl = Resources.getResource(relativeResource)
                val body = Resources.toByteArray(resourceUrl)
//                println("%%%%% read resource: relativePath")
                return body
            }
            catch (ignored: Exception) {}
        }

        for (root in resourceDirectories) {
            val candidate = root.resolve(relativePath)
            if (! Files.exists(candidate)) {
                println("%%%%% no file at: ${candidate.toAbsolutePath()}")
                continue
            }

            val body = Files.readAllBytes(candidate)
//            println("%%%%% read file: ${candidate.toAbsolutePath()}")
            return body
        }

        println("%%%%% not read: ${relativePath}")
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
}