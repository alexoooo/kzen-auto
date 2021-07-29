package tech.kzen.auto.server.api

import com.google.common.io.MoreFiles
import com.google.common.io.Resources
import kotlinx.coroutines.runBlocking
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunId
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResponse
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.ImmutableByteArray
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Collectors


// TODO: refactor
@Component
class RestHandler {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val classPathRoots = listOf(
            URI("classpath:/public/")
        )

        val resourceDirectories = discoverResourceDirectories()

        private const val cssExtension = "css"

        val allowedExtensions = listOf(
            "html",
            "js",
            cssExtension,
            "svg",
            "png",
            "ico"
        )

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
                builder.add(Paths.get("$projectName-js/build/distributions/"))

                // Eclipse and Gradle default active working directory is the module
                builder.add(Paths.get("src/main/resources/public/"))
                builder.add(Paths.get("../$projectName-js/build/distributions/"))
            }
            else {
                builder.add(Paths.get("static/"))
            }

            return builder
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun scan(serverRequest: ServerRequest): Mono<ServerResponse> {
        val fresh = serverRequest.queryParams().containsKey(CommonRestApi.paramFresh)

        if (fresh) {
            ServerContext.notationMedia.invalidate()
        }

        val documentTree = runBlocking {
            ServerContext.notationMedia.scan()
        }

        val asMap = mutableMapOf<String, Any>()

        for (e in documentTree.documents.values) {
            asMap[e.key.asRelativeFile()] = mapOf(
                "documentDigest" to e.value.documentDigest.asString(),
                "resources" to e.value.resources?.digests?.map {
                    it.key.asString() to it.value.asString()
                }?.toMap()
            )
        }

        return ServerResponse
                .ok()
                .body(Mono.just(asMap))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun resourceRead(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val resourcePath: ResourcePath = serverRequest.getParam(
            CommonRestApi.paramResourcePath, ResourcePath::parse)

        val resourceLocation = ResourceLocation(documentPath, resourcePath)

        val resourceContents = runBlocking {
            ServerContext.notationMedia.readResource(resourceLocation)
        }

        return ServerResponse
                .ok()
                .body(Mono.just(resourceContents.toByteArray()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun notation(serverRequest: ServerRequest): Mono<ServerResponse> {
        val encodedRequestSuffix = serverRequest.path().substring(CommonRestApi.notationPrefix.length)
        val requestSuffix = URI(encodedRequestSuffix).path

        val notationPath = DocumentPath.parse(requestSuffix)
        val notationText = runBlocking {
            ServerContext.notationMedia.readDocument(notationPath)
        }

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
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val documentBody = serverRequest.getParam(CommonRestApi.paramDocumentNotation) {
            ServerContext.yamlParser.parseDocumentObjects(it)
        }

        return applyAndDigest(
            CreateDocumentCommand(documentPath, documentBody))
    }


    fun deleteDocument(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        return applyAndDigest(
            DeleteDocumentCommand(documentPath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun addObject(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val indexInDocument: PositionRelation = serverRequest.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

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
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        return applyAndDigest(
            RemoveObjectCommand(
                ObjectLocation(documentPath, objectPath)))
    }


    fun shiftObject(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val indexInDocument: PositionRelation = serverRequest.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

        return applyAndDigest(
            ShiftObjectCommand(
                ObjectLocation(documentPath, objectPath),
                indexInDocument))
    }


    fun renameObject(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectName: ObjectName = serverRequest.getParam(
            CommonRestApi.paramObjectName, ::ObjectName)

        return applyAndDigest(
            RenameObjectCommand(
                ObjectLocation(documentPath, objectPath),
                objectName))
    }


    fun insertObjectInList(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val containingObjectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val containingList: AttributePath = serverRequest.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val indexInList: PositionRelation = serverRequest.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

        val objectName: ObjectName = serverRequest.getParam(
            CommonRestApi.paramObjectName, ::ObjectName)

        val positionInDocument: PositionRelation = serverRequest.getParam(
            CommonRestApi.paramSecondaryPosition, PositionRelation::parse)

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
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val containingObjectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributePath: AttributePath = serverRequest.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        return applyAndDigest(
            RemoveObjectInAttributeCommand(
                ObjectLocation(documentPath, containingObjectPath),
                attributePath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun upsertAttribute(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributeName: AttributeName = serverRequest.getParam(
            CommonRestApi.paramAttributeName, AttributeName::parse)

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
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributePath: AttributePath = serverRequest.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val attributeNotation: AttributeNotation = serverRequest.getParam(
            CommonRestApi.paramAttributeNotation, ServerContext.yamlParser::parseAttribute)

        return applyAndDigest(
            UpdateInAttributeCommand(
                ObjectLocation(documentPath, objectPath),
                attributePath,
                attributeNotation))
    }


    fun updateAllNestingsInAttributeGet(serverRequest: ServerRequest): Mono<ServerResponse> {
        return updateAllNestingsInAttributeImpl(serverRequest.queryParams())
    }


    fun updateAllNestingsInAttributePut(serverRequest: ServerRequest): Mono<ServerResponse> {
        return serverRequest.formData().flatMap { body ->
            updateAllNestingsInAttributeImpl(body)
        }
    }


    private fun updateAllNestingsInAttributeImpl(params: MultiValueMap<String, String>): Mono<ServerResponse> {
        val documentPath: DocumentPath = params.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = params.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributeName: AttributeName = params.getParam(
            CommonRestApi.paramAttributeName, AttributeName::parse)

        val attributeNestings: List<AttributeNesting> = params.getParamList(
            CommonRestApi.paramAttributeNesting, AttributeNesting::parse)

        val attributeNotation: AttributeNotation = params.getParam(
            CommonRestApi.paramAttributeNotation, ServerContext.yamlParser::parseAttribute)

        return applyAndDigest(
            UpdateAllNestingsInAttributeCommand(
                ObjectLocation(documentPath, objectPath),
                attributeName,
                attributeNestings,
                attributeNotation))
    }


    fun updateAllValuesInAttributeGet(serverRequest: ServerRequest): Mono<ServerResponse> {
        return updateAllValuesInAttributeImpl(serverRequest.queryParams())
    }


    fun updateAllValuesInAttributePut(serverRequest: ServerRequest): Mono<ServerResponse> {
        return serverRequest.formData().flatMap { body ->
            updateAllValuesInAttributeImpl(body)
        }
    }


    private fun updateAllValuesInAttributeImpl(params: MultiValueMap<String, String>): Mono<ServerResponse> {
        val documentPath: DocumentPath = params.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = params.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributeName: AttributeName = params.getParam(
            CommonRestApi.paramAttributeName, AttributeName::parse)

        val attributeNestings: List<AttributeNesting> = params.getParamList(
            CommonRestApi.paramAttributeNesting, AttributeNesting::parse)

        val attributeNotations: List<AttributeNotation> = params.getParamList(
            CommonRestApi.paramAttributeNotation, ServerContext.yamlParser::parseAttribute)

        require(attributeNestings.size == attributeNotations.size)

        val nestingNotations = attributeNestings.zip(attributeNotations).toMap()

        return applyAndDigest(
            UpdateAllValuesInAttributeCommand(
                ObjectLocation(documentPath, objectPath),
                attributeName,
                nestingNotations))
    }


    fun insertListItemInAttribute(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val containingList: AttributePath = serverRequest.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val indexInList: PositionRelation = serverRequest.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

        val itemNotation: AttributeNotation = serverRequest.getParam(
            CommonRestApi.paramAttributeNotation, ServerContext.yamlParser::parseAttribute)

        return applyAndDigest(
            InsertListItemInAttributeCommand(
                ObjectLocation(documentPath, objectPath),
                containingList,
                indexInList,
                itemNotation))
    }


    fun insertAllListItemsInAttributeGet(serverRequest: ServerRequest): Mono<ServerResponse> {
        return insertAllListItemsInAttributeImpl(serverRequest.queryParams())
    }


    fun insertAllListItemsInAttributePut(serverRequest: ServerRequest): Mono<ServerResponse> {
        return serverRequest.formData().flatMap { body ->
            insertAllListItemsInAttributeImpl(body)
        }
    }


    private fun insertAllListItemsInAttributeImpl(params: MultiValueMap<String, String>): Mono<ServerResponse> {
        val documentPath: DocumentPath = params.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = params.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val containingList: AttributePath = params.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val indexInList: PositionRelation = params.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

        val itemNotations: List<AttributeNotation> = params.getParamList(
            CommonRestApi.paramAttributeNotation, ServerContext.yamlParser::parseAttribute)

        return applyAndDigest(
            InsertAllListItemsInAttributeCommand(
                ObjectLocation(documentPath, objectPath),
                containingList,
                indexInList,
                itemNotations))
    }


    fun insertMapEntryInAttribute(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val containingMap: AttributePath = serverRequest.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val indexInMap: PositionRelation = serverRequest.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

        val mapKey: AttributeSegment = serverRequest.getParam(
            CommonRestApi.paramAttributeKey, AttributeSegment::parse)

        val valueNotation: AttributeNotation = serverRequest.getParam(
            CommonRestApi.paramAttributeNotation, ServerContext.yamlParser::parseAttribute)

        val createAncestorsIfAbsent: Boolean = serverRequest
            .tryGetParam(CommonRestApi.paramAttributeCreateContainer) { value -> value == "true" }
            ?: false

        return applyAndDigest(
            InsertMapEntryInAttributeCommand(
                ObjectLocation(documentPath, objectPath),
                containingMap,
                indexInMap,
                mapKey,
                valueNotation,
                createAncestorsIfAbsent))
    }


    fun removeInAttribute(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributePath: AttributePath = serverRequest.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val removeContainerIfEmpty: Boolean = serverRequest
            .tryGetParam(CommonRestApi.paramAttributeCleanupContainer) { i -> i == "true"}
            ?: false

        return applyAndDigest(
            RemoveInAttributeCommand(
                ObjectLocation(documentPath, objectPath),
                attributePath,
                removeContainerIfEmpty
            )
        )
    }


    fun removeListItemInAttribute(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributePath: AttributePath = serverRequest.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val itemNotation: AttributeNotation = serverRequest.getParam(
            CommonRestApi.paramAttributeNotation, ServerContext.yamlParser::parseAttribute)

        val removeContainerIfEmpty: Boolean = serverRequest
            .tryGetParam(CommonRestApi.paramAttributeCleanupContainer) { i -> i == "true"}
            ?: false

        return applyAndDigest(
            RemoveListItemInAttributeCommand(
                ObjectLocation(documentPath, objectPath),
                attributePath,
                itemNotation,
                removeContainerIfEmpty))
    }


    fun removeAllListItemsInAttributeGet(serverRequest: ServerRequest): Mono<ServerResponse> {
        return removeAllListItemsInAttributeImpl(serverRequest.queryParams())
    }


    fun removeAllListItemsInAttributePut(serverRequest: ServerRequest): Mono<ServerResponse> {
        return serverRequest.formData().flatMap { body ->
            removeAllListItemsInAttributeImpl(body)
        }
    }


    private fun removeAllListItemsInAttributeImpl(params: MultiValueMap<String, String>): Mono<ServerResponse> {
        val documentPath: DocumentPath = params.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = params.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributePath: AttributePath = params.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val itemNotations: List<AttributeNotation> = params.getParamList(
            CommonRestApi.paramAttributeNotation, ServerContext.yamlParser::parseAttribute)

        val removeContainerIfEmpty: Boolean = params
            .tryGetParam(CommonRestApi.paramAttributeCleanupContainer) { i -> i == "true"}
            ?: false

        return applyAndDigest(
            RemoveAllListItemsInAttributeCommand(
                ObjectLocation(documentPath, objectPath),
                attributePath,
                itemNotations,
                removeContainerIfEmpty))
    }


    fun shiftInAttribute(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributePath: AttributePath = serverRequest.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val newPosition: PositionRelation = serverRequest.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

        return applyAndDigest(
            ShiftInAttributeCommand(
                ObjectLocation(documentPath, objectPath),
                attributePath,
                newPosition))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun refactorObjectName(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val newName: ObjectName = serverRequest.getParam(
            CommonRestApi.paramObjectName, ::ObjectName)

        return applyAndDigest(
            RenameObjectRefactorCommand(
                ObjectLocation(documentPath, objectPath),
                newName))
    }


    fun refactorDocumentName(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val newName: DocumentName = serverRequest.getParam(
            CommonRestApi.paramDocumentName, ::DocumentName)

        return applyAndDigest(
            RenameDocumentRefactorCommand(
                documentPath,
                newName))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun addResource(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val resourcePath: ResourcePath = serverRequest.getParam(
            CommonRestApi.paramResourcePath, ResourcePath::parse)

        val contents = serverRequest.bodyToMono(ByteArray::class.java)

        return contents.flatMap {
            val command = AddResourceCommand(
                ResourceLocation(documentPath, resourcePath),
                ImmutableByteArray.wrap(it))

            val digest = applyCommand(command)

            ServerResponse
                .ok()
                .body(Mono.just(digest.asString()))
        }
    }


    fun resourceDelete(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val resourcePath: ResourcePath = serverRequest.getParam(
            CommonRestApi.paramResourcePath, ResourcePath::parse)

        return applyAndDigest(
            RemoveResourceCommand(
                ResourceLocation(documentPath, resourcePath)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun benchmark(serverRequest: ServerRequest): Mono<ServerResponse> {
        val iterations: Int = serverRequest.getParam(
            "i", Integer::parseInt)

        val startTime = System.currentTimeMillis()

        // http://localhost:8080/command/object/insert-in-list?path=main%2FScript.yaml&object=main&in-attribute=steps&index=7&name=Escape&position=8&body=is%3A%20SendEscape
        val addCommand = InsertObjectInListAttributeCommand(
            ObjectLocation.parse("main/Script.yaml#main"),
            AttributePath.parse("steps"),
            PositionRelation.parse("7"),
            ObjectName("Escape"),
            PositionRelation.parse("8"),
            ServerContext.yamlParser.parseObject("is: SendEscape"))

        // http://localhost:8080/command/object/remove-in?path=main%2FScript.yaml&object=main&in-attribute=steps.7
        val removeCommand = RemoveObjectInAttributeCommand(
            ObjectLocation.parse("main/Script.yaml#main"),
            AttributePath.parse("steps.7"))

        for (i in 0 .. iterations) {
            applyAndDigest(addCommand)
            applyAndDigest(removeCommand)
        }

        val duration = System.currentTimeMillis() - startTime
        return ServerResponse
                .ok()
                .body(Mono.just("$duration"))
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
            try {
                ServerContext.graphStore.apply(command)
            }
            catch (e: Exception) {
                e.printStackTrace()
            }

            ServerContext.graphStore.digest()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun actionList(serverRequest: ServerRequest): Mono<ServerResponse> {
        val activeScripts = ServerContext.executionRepository.activeScripts()
        val encoded = activeScripts.map { it.asString() }

        return ServerResponse
                .ok()
                .body(Mono.just(encoded))
    }


    fun actionModel(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse
        )

        val executionModel = runBlocking {
            val graphStructure = ServerContext.graphStore.graphStructure()
            ServerContext.executionRepository.executionModel(documentPath, graphStructure)
        }

        return ServerResponse
                .ok()
                .body(Mono.just(ImperativeModel.toCollection(executionModel)))
    }


    fun actionStart(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse
        )

        val digest = runBlocking {
            val graphStructure = ServerContext.graphStore
                    .graphStructure()
                    .filter(AutoConventions.serverAllowed)

            ServerContext.executionRepository.start(
                documentPath, graphStructure
            )
        }

        return ServerResponse
                .ok()
                .body(Mono.just(digest.asString()))
    }


    fun actionReturn(serverRequest: ServerRequest): Mono<ServerResponse> {
        val hostDocumentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramHostDocumentPath, DocumentPath::parse
        )

//        val documentPath: DocumentPath = serverRequest.getParam(
//                CommonRestApi.paramDocumentPath, DocumentPath.Companion::parse)

        val digest = runBlocking {
            val graphStructure = ServerContext.graphStore
                    .graphStructure()
                    .filter(AutoConventions.serverAllowed)

            ServerContext.executionRepository.returnFrame(
                hostDocumentPath, graphStructure
            )
        }

        return ServerResponse
                .ok()
                .body(Mono.just(digest.asString()))
    }


    fun actionReset(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse
        )

//        val digest = runBlocking {
        runBlocking {
            ServerContext.executionRepository.reset(documentPath)
        }

        return ServerResponse
                .ok()
                .build()
//                .body(Mono.just(digest.asString()))
    }


    fun actionPerform(serverRequest: ServerRequest): Mono<ServerResponse> {
        val hostDocumentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramHostDocumentPath, DocumentPath::parse
        )

        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse
        )

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse
        )

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val execution: ImperativeResponse = runBlocking {
            val graphStructure = ServerContext.graphStore.graphStructure()
            ServerContext.executionRepository.execute(
                hostDocumentPath, objectLocation, graphStructure
            )
        }

        return ServerResponse
                .ok()
                .body(Mono.just(execution.toCollection()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun actionDetachedByQuery(serverRequest: ServerRequest): Mono<ServerResponse> {
        return actionDetachedImpl(serverRequest, serverRequest.queryParams())
    }


    fun actionDetachedByForm(serverRequest: ServerRequest): Mono<ServerResponse> {
        return serverRequest.formData().flatMap { params ->
            actionDetachedImpl(serverRequest, params)
        }
    }


    private fun actionDetachedImpl(
        serverRequest: ServerRequest,
        params: MultiValueMap<String, String>
    ): Mono<ServerResponse> {
        val documentPath: DocumentPath = params.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = params.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val detachedParams = mutableMapOf<String, List<String>>()
        for (e in params) {
            if (e.key == CommonRestApi.paramDocumentPath ||
                    e.key == CommonRestApi.paramObjectPath
            ) {
                continue
            }
            detachedParams[e.key] = e.value
        }

        return serverRequest
            .bodyToMono(ByteArray::class.java)
            .map { Optional.of(ImmutableByteArray.wrap(it)) }
            .defaultIfEmpty(Optional.empty())
            .flatMap { optionalBody ->
                val body = optionalBody.orElse(null)

                val detachedRequest = ExecutionRequest(RequestParams(detachedParams), body)

                val execution: ExecutionResult = runBlocking {
                    ServerContext.detachedExecutor.execute(
                        objectLocation,
                        detachedRequest
                    )
                }

                ServerResponse
                    .ok()
                    .body(Mono.just(execution.toJsonCollection()))
            }
    }


    fun actionDetachedDownload(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val params = mutableMapOf<String, List<String>>()
        for (e in serverRequest.queryParams()) {
            if (e.key == CommonRestApi.paramDocumentPath ||
                    e.key == CommonRestApi.paramObjectPath) {
                continue
            }
            params[e.key] = e.value
        }

        return serverRequest
                .bodyToMono(ByteArray::class.java)
                .map { Optional.of(ImmutableByteArray.wrap(it)) }
                .defaultIfEmpty(Optional.empty())
                .flatMap { optionalBody ->
                    val body = optionalBody.orElse(null)

                    val detachedRequest = ExecutionRequest(RequestParams(params), body)

                    val execution: ExecutionDownloadResult = runBlocking {
                        ServerContext.detachedExecutor.executeDownload(
                            objectLocation, detachedRequest)
                    }

                    val contentType = MediaType.parseMediaType(execution.mimeType)
                    val attachmentFilename = "attachment; filename*=utf-8''" + execution.fileName
                    val resource: Resource = InputStreamResource(execution.data)

                    ServerResponse
                        .ok()
                        .contentType(contentType)
                        .header(HttpHeaders.CONTENT_DISPOSITION, attachmentFilename)
                        .body(Mono.just(resource))
                }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun execModel(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath? = serverRequest.tryGetParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val visualDataflowModel = runBlocking {
            ServerContext.visualDataflowRepository.get(documentPath)
        }

        val result =
                if (objectPath == null) {
                    VisualDataflowModel.toJsonCollection(visualDataflowModel)
                }
                else {
                    val objectLocation = ObjectLocation(documentPath, objectPath)
                    val visualVertexModel = visualDataflowModel.vertices[objectLocation]
                            ?: throw IllegalArgumentException("Object location not found: $objectLocation")

                    VisualVertexModel.toJsonCollection(visualVertexModel)
                }

        return ServerResponse
                .ok()
                .body(Mono.just(result))
    }


    fun execReset(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val visualDataflowModel = runBlocking {
            ServerContext.visualDataflowRepository.reset(documentPath)
        }

        return ServerResponse
                .ok()
                .body(Mono.just(VisualDataflowModel.toJsonCollection(visualDataflowModel)))
    }


    fun execPerform(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val transition: VisualVertexTransition = runBlocking {
            ServerContext.visualDataflowRepository.execute(documentPath, objectLocation)
        }

        return ServerResponse
                .ok()
                .body(Mono.just(VisualVertexTransition.toCollection(transition)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun taskSubmit(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val params = mutableMapOf<String, List<String>>()
        for (e in serverRequest.queryParams()) {
            if (e.key == CommonRestApi.paramDocumentPath ||
                e.key == CommonRestApi.paramObjectPath) {
                continue
            }
            params[e.key] = e.value
        }

        return serverRequest
            .bodyToMono(ByteArray::class.java)
            .map { Optional.of(ImmutableByteArray.wrap(it)) }
            .defaultIfEmpty(Optional.empty())
            .flatMap { optionalBody ->
                val body = optionalBody.orElse(null)

                val detachedRequest = ExecutionRequest(RequestParams(params), body)

                val execution: TaskModel = runBlocking {
                    ServerContext.modelTaskRepository.submit(
                        objectLocation,
                        detachedRequest)
                }

                ServerResponse
                    .ok()
                    .body(Mono.just(execution.toJsonCollection()))
            }
    }


    fun taskQuery(serverRequest: ServerRequest): Mono<ServerResponse> {
        val taskId: TaskId = serverRequest
            .getParam(CommonRestApi.paramTaskId) { TaskId(it) }

        val model: TaskModel = runBlocking {
            ServerContext.modelTaskRepository.query(taskId)
        }
            ?: return ServerResponse.noContent().build()

        val result = model.toJsonCollection()

        return ServerResponse
            .ok()
            .body(Mono.just(result))
    }


    fun taskCancel(serverRequest: ServerRequest): Mono<ServerResponse> {
        val taskId: TaskId = serverRequest
            .getParam(CommonRestApi.paramTaskId) { TaskId(it) }

        val model: TaskModel = runBlocking {
            ServerContext.modelTaskRepository.cancel(taskId)
        }
            ?: return ServerResponse.noContent().build()

        val result = model.toJsonCollection()

        return ServerResponse
            .ok()
            .body(Mono.just(result))
    }


    fun taskLookup(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val tasks: Set<TaskId> = runBlocking {
            ServerContext.modelTaskRepository.lookupActive(objectLocation)
        }

        return ServerResponse
            .ok()
            .body(Mono.just(tasks.map { it.identifier }))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun logicStatus(serverRequest: ServerRequest): Mono<ServerResponse> {
        val status = ServerContext.serverLogicController.status()

        return ServerResponse
            .ok()
            .body(Mono.just(status.toCollection()))
    }


    fun logicStart(serverRequest: ServerRequest): Mono<ServerResponse> {
        val documentPath: DocumentPath = serverRequest.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = serverRequest.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val logicRunId = runBlocking {
            ServerContext.serverLogicController.start(objectLocation)
        }

        @Suppress("FoldInitializerAndIfToElvis")
        if (logicRunId == null) {
            return ServerResponse.badRequest().build()
        }

        return ServerResponse
            .ok()
            .body(Mono.just(logicRunId.value))
    }


    fun logicRequest(serverRequest: ServerRequest): Mono<ServerResponse> {
        val runId: LogicRunId = serverRequest.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val executionId: LogicExecutionId = serverRequest.getParam(CommonRestApi.paramExecutionId) {
            value -> LogicExecutionId(value)
        }

        val params = mutableMapOf<String, List<String>>()
        for (e in serverRequest.queryParams()) {
            if (e.key == CommonRestApi.paramRunId ||
                e.key == CommonRestApi.paramExecutionId) {
                continue
            }
            params[e.key] = e.value
        }

        return serverRequest
            .bodyToMono(ByteArray::class.java)
            .map { Optional.of(ImmutableByteArray.wrap(it)) }
            .defaultIfEmpty(Optional.empty())
            .flatMap { optionalBody ->
                val body = optionalBody.orElse(null)

                val request = ExecutionRequest(RequestParams(params), body)

                val result: ExecutionResult = runBlocking {
                    ServerContext.serverLogicController.request(
                        runId,
                        executionId,
                        request)
                }

                ServerResponse
                    .ok()
                    .body(Mono.just(result.toJsonCollection()))
            }
    }


    fun logicCancel(serverRequest: ServerRequest): Mono<ServerResponse> {
        val runId: LogicRunId = serverRequest.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val response = runBlocking {
            ServerContext.serverLogicController.cancel(runId)
        }

        return ServerResponse
            .ok()
            .body(Mono.just(response.name))
    }


    fun logicRun(serverRequest: ServerRequest): Mono<ServerResponse> {
        val runId: LogicRunId = serverRequest.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val response = runBlocking {
            ServerContext.serverLogicController.run(runId)
        }

        return ServerResponse
            .ok()
            .body(Mono.just(response.name))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // TODO: is this secure?
    fun staticResource(serverRequest: ServerRequest): Mono<ServerResponse> {
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
//        return queryParam(parameterName)
//                .map { parser(it) }
//                .orElseThrow { IllegalArgumentException("'$parameterName' required") }
        return queryParams().getParam(parameterName, parser)
    }


    private fun <T> MultiValueMap<String, String>.getParam(
        parameterName: String,
        parser: (String) -> T
    ): T {
        val queryParamValues: List<String>? = get(parameterName)
        require(! queryParamValues.isNullOrEmpty()) { "'$parameterName' required" }
        return parser(queryParamValues.first())
    }


    private fun <T> ServerRequest.getParamList(
        parameterName: String,
        parser: (String) -> T
    ): List<T> {
//        val itemNotations = queryParams()[parameterName]
//            ?: throw IllegalArgumentException("'$parameterName' required")
//        return itemNotations.map { i -> parser(i) }
        return queryParams().getParamList(parameterName, parser)
    }


    private fun <T> MultiValueMap<String, String>.getParamList(
        parameterName: String,
        parser: (String) -> T
    ): List<T> {
        val queryParamValues: List<String> = get(parameterName) ?: listOf()
        return queryParamValues.map(parser)
    }


    private fun <T> ServerRequest.tryGetParam(
        parameterName: String,
        parser: (String) -> T
    ): T? {
//        return queryParam(parameterName)
//                .map { parser(it) }
//                .orElse(null)
        return queryParams().tryGetParam(parameterName, parser)
    }


    private fun <T> MultiValueMap<String, String>.tryGetParam(
        parameterName: String,
        parser: (String) -> T
    ): T? {
        return get(parameterName)?.singleOrNull()?.let { parser(it) }
    }
}