package tech.kzen.auto.server.api

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunResponse
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowRepository
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResponse
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionRepository
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.auto.server.service.exec.ModelDetachedExecutor
import tech.kzen.auto.server.service.exec.ModelTaskRepository
import tech.kzen.auto.server.service.v1.impl.ServerLogicController
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
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.common.service.store.DirectGraphStore
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.ImmutableByteArray
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors


class RestHandler(
    private val notationMedia: NotationMedia,
    private val yamlNotationParser: YamlNotationParser,
    private val graphStore: DirectGraphStore,
    private val executionRepository: ExecutionRepository,
    private val detachedExecutor: ModelDetachedExecutor,
    private val visualDataflowRepository: VisualDataflowRepository,
    private val modelTaskRepository: ModelTaskRepository,
    private val serverLogicController: ServerLogicController
) {
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

//        private val cssMediaType = MediaType.valueOf("text/css")


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
    fun scan(parameters: Parameters): Map<String, Any> {
        val fresh = parameters[CommonRestApi.paramFresh] == "true"
        return scan(fresh)
    }


    fun scan(fresh: Boolean): Map<String, Any> {
        if (fresh) {
            notationMedia.invalidate()
        }

        val documentTree = runBlocking {
            notationMedia.scan()
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

        return asMap
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun resourceRead(parameters: Parameters): ByteArray {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val resourcePath: ResourcePath = parameters.getParam(
            CommonRestApi.paramResourcePath, ResourcePath::parse)

        val resourceLocation = ResourceLocation(documentPath, resourcePath)

        val resourceContents = runBlocking {
            notationMedia.readResource(resourceLocation)
        }

        return resourceContents.toByteArray()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun notation(notationPath: String, notationPathUrlEncoded: Boolean): String {
        val decodedNotationPath =
            if (notationPathUrlEncoded) {
                URI(notationPath).path
            }
            else {
                notationPath
            }

        val parsedNotationPath = DocumentPath.parse(decodedNotationPath)
        val notationText = runBlocking {
            notationMedia.readDocument(parsedNotationPath)
        }
        return notationText
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun createDocument(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val documentBody = parameters.getParam(CommonRestApi.paramDocumentNotation) {
            yamlNotationParser.parseDocumentObjects(it)
        }

        val command = CreateDocumentCommand(documentPath, documentBody)
        return applyCommand(command).asString()
    }


    fun deleteDocument(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val command = DeleteDocumentCommand(documentPath)
        return applyCommand(command).asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun addObject(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val indexInDocument: PositionRelation = parameters.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

        val objectNotation: ObjectNotation = parameters.getParam(
            CommonRestApi.paramObjectNotation, yamlNotationParser::parseObject)

        val command = AddObjectCommand(
            ObjectLocation(documentPath, objectPath),
            indexInDocument,
            objectNotation)

        return applyCommand(command).asString()
    }


    fun removeObject(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val command = RemoveObjectCommand(
            ObjectLocation(documentPath, objectPath))

        return applyCommand(command).asString()
    }


    fun shiftObject(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val indexInDocument: PositionRelation = parameters.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

        val command = ShiftObjectCommand(
            ObjectLocation(documentPath, objectPath),
            indexInDocument)

        return applyCommand(command).asString()
    }


    fun renameObject(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectName: ObjectName = parameters.getParam(
            CommonRestApi.paramObjectName, ::ObjectName)

        val command = RenameObjectCommand(
            ObjectLocation(documentPath, objectPath),
            objectName)

        return applyCommand(command).asString()
    }


    fun insertObjectInList(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val containingObjectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val containingList: AttributePath = parameters.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val indexInList: PositionRelation = parameters.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

        val objectName: ObjectName = parameters.getParam(
            CommonRestApi.paramObjectName, ::ObjectName)

        val positionInDocument: PositionRelation = parameters.getParam(
            CommonRestApi.paramSecondaryPosition, PositionRelation::parse)

        val objectNotation: ObjectNotation = parameters.getParam(
            CommonRestApi.paramObjectNotation, yamlNotationParser::parseObject)

        val command = InsertObjectInListAttributeCommand(
            ObjectLocation(documentPath, containingObjectPath),
            containingList,
            indexInList,
            objectName,
            positionInDocument,
            objectNotation)

        return applyCommand(command).asString()
    }


    fun removeObjectInAttribute(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val containingObjectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributePath: AttributePath = parameters.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val command = RemoveObjectInAttributeCommand(
            ObjectLocation(documentPath, containingObjectPath),
            attributePath)

        return applyCommand(command).asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun upsertAttribute(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributeName: AttributeName = parameters.getParam(
            CommonRestApi.paramAttributeName, AttributeName::parse)

        val attributeNotation: AttributeNotation = parameters.getParam(
            CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

        val command = UpsertAttributeCommand(
            ObjectLocation(documentPath, objectPath),
            attributeName,
            attributeNotation)

        return applyCommand(command).asString()
    }


    fun updateInAttribute(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributePath: AttributePath = parameters.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val attributeNotation: AttributeNotation = parameters.getParam(
            CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

        val command = UpdateInAttributeCommand(
            ObjectLocation(documentPath, objectPath),
            attributePath,
            attributeNotation)

        return applyCommand(command).asString()
    }


    fun updateAllNestingsInAttribute(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributeName: AttributeName = parameters.getParam(
            CommonRestApi.paramAttributeName, AttributeName::parse)

        val attributeNestings: List<AttributeNesting> = parameters.getParamList(
            CommonRestApi.paramAttributeNesting, AttributeNesting::parse)

        val attributeNotation: AttributeNotation = parameters.getParam(
            CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

        val command = UpdateAllNestingsInAttributeCommand(
            ObjectLocation(documentPath, objectPath),
            attributeName,
            attributeNestings,
            attributeNotation)

        return applyCommand(command).asString()
    }


    fun updateAllValuesInAttribute(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributeName: AttributeName = parameters.getParam(
            CommonRestApi.paramAttributeName, AttributeName::parse)

        val attributeNestings: List<AttributeNesting> = parameters.getParamList(
            CommonRestApi.paramAttributeNesting, AttributeNesting::parse)

        val attributeNotations: List<AttributeNotation> = parameters.getParamList(
            CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

        require(attributeNestings.size == attributeNotations.size)

        val nestingNotations = attributeNestings.zip(attributeNotations).toMap()

        val command = UpdateAllValuesInAttributeCommand(
            ObjectLocation(documentPath, objectPath),
            attributeName,
            nestingNotations)

        return applyCommand(command).asString()
    }


    fun insertListItemInAttribute(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val containingList: AttributePath = parameters.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val indexInList: PositionRelation = parameters.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

        val itemNotation: AttributeNotation = parameters.getParam(
            CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

        val command = InsertListItemInAttributeCommand(
            ObjectLocation(documentPath, objectPath),
            containingList,
            indexInList,
            itemNotation)

        return applyCommand(command).asString()
    }


    fun insertAllListItemsInAttribute(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val containingList: AttributePath = parameters.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val indexInList: PositionRelation = parameters.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

        val itemNotations: List<AttributeNotation> = parameters.getParamList(
            CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

        val command = InsertAllListItemsInAttributeCommand(
            ObjectLocation(documentPath, objectPath),
            containingList,
            indexInList,
            itemNotations)

        return applyCommand(command).asString()
    }


    fun insertMapEntryInAttribute(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val containingMap: AttributePath = parameters.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val indexInMap: PositionRelation = parameters.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

        val mapKey: AttributeSegment = parameters.getParam(
            CommonRestApi.paramAttributeKey, AttributeSegment::parse)

        val valueNotation: AttributeNotation = parameters.getParam(
            CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

        val createAncestorsIfAbsent: Boolean = parameters
            .getParamOrNull(CommonRestApi.paramAttributeCreateContainer) { value -> value == "true" }
            ?: false

        val command = InsertMapEntryInAttributeCommand(
            ObjectLocation(documentPath, objectPath),
            containingMap,
            indexInMap,
            mapKey,
            valueNotation,
            createAncestorsIfAbsent)

        return applyCommand(command).asString()
    }


    fun removeInAttribute(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributePath: AttributePath = parameters.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val removeContainerIfEmpty: Boolean = parameters
            .getParamOrNull(CommonRestApi.paramAttributeCleanupContainer) { i -> i == "true"}
            ?: false

        val command = RemoveInAttributeCommand(
            ObjectLocation(documentPath, objectPath),
            attributePath,
            removeContainerIfEmpty)

        return applyCommand(command).asString()
    }


    fun removeListItemInAttribute(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributePath: AttributePath = parameters.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val itemNotation: AttributeNotation = parameters.getParam(
            CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

        val removeContainerIfEmpty: Boolean = parameters
            .getParamOrNull(CommonRestApi.paramAttributeCleanupContainer) { i -> i == "true"}
            ?: false

        val command = RemoveListItemInAttributeCommand(
            ObjectLocation(documentPath, objectPath),
            attributePath,
            itemNotation,
            removeContainerIfEmpty)

        return applyCommand(command).asString()
    }


    fun removeAllListItemsInAttribute(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributePath: AttributePath = parameters.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val itemNotations: List<AttributeNotation> = parameters.getParamList(
            CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

        val removeContainerIfEmpty: Boolean = parameters
            .getParamOrNull(CommonRestApi.paramAttributeCleanupContainer) { i -> i == "true"}
            ?: false

        val command = RemoveAllListItemsInAttributeCommand(
            ObjectLocation(documentPath, objectPath),
            attributePath,
            itemNotations,
            removeContainerIfEmpty)

        return applyCommand(command).asString()
    }


    fun shiftInAttribute(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val attributePath: AttributePath = parameters.getParam(
            CommonRestApi.paramAttributePath, AttributePath::parse)

        val newPosition: PositionRelation = parameters.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

        val command = ShiftInAttributeCommand(
            ObjectLocation(documentPath, objectPath),
            attributePath,
            newPosition)

        return applyCommand(command).asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun refactorObjectName(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val newName: ObjectName = parameters.getParam(
            CommonRestApi.paramObjectName, ::ObjectName)

        val command = RenameObjectRefactorCommand(
            ObjectLocation(documentPath, objectPath),
            newName)

        return applyCommand(command).asString()
    }


    fun refactorDocumentName(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val newName: DocumentName = parameters.getParam(
            CommonRestApi.paramDocumentName, ::DocumentName)

        val command = RenameDocumentRefactorCommand(
            documentPath, newName)

        return applyCommand(command).asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun addResource(parameters: Parameters, body: ImmutableByteArray): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val resourcePath: ResourcePath = parameters.getParam(
            CommonRestApi.paramResourcePath, ResourcePath::parse)

        val command = AddResourceCommand(
            ResourceLocation(documentPath, resourcePath),
            body)

        return applyCommand(command).asString()
    }


    fun resourceDelete(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val resourcePath: ResourcePath = parameters.getParam(
            CommonRestApi.paramResourcePath, ResourcePath::parse)

        val command = RemoveResourceCommand(
            ResourceLocation(documentPath, resourcePath))

        return applyCommand(command).asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun benchmark(serverRequest: ServerRequest): Mono<ServerResponse> {
//        val iterations: Int = serverRequest.getParam(
//            "i", Integer::parseInt)
//
//        val startTime = System.currentTimeMillis()
//
//        // http://localhost:8080/command/object/insert-in-list?path=main%2FScript.yaml&object=main&in-attribute=steps&index=7&name=Escape&position=8&body=is%3A%20SendEscape
//        val addCommand = InsertObjectInListAttributeCommand(
//            ObjectLocation.parse("main/Script.yaml#main"),
//            AttributePath.parse("steps"),
//            PositionRelation.parse("7"),
//            ObjectName("Escape"),
//            PositionRelation.parse("8"),
//            ServerContext.yamlParser.parseObject("is: SendEscape"))
//
//        // http://localhost:8080/command/object/remove-in?path=main%2FScript.yaml&object=main&in-attribute=steps.7
//        val removeCommand = RemoveObjectInAttributeCommand(
//            ObjectLocation.parse("main/Script.yaml#main"),
//            AttributePath.parse("steps.7"))
//
//        for (i in 0 .. iterations) {
//            applyAndDigest(addCommand)
//            applyAndDigest(removeCommand)
//        }
//
//        val duration = System.currentTimeMillis() - startTime
//        return ServerResponse
//                .ok()
//                .body(Mono.just("$duration"))
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun applyCommand(command: NotationCommand): Digest {
        return runBlocking {
            try {
                graphStore.apply(command)
            }
            catch (e: Exception) {
                e.printStackTrace()
            }

            graphStore.digest()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun actionList(): List<String> {
        val activeScripts = executionRepository.activeScripts()
        return activeScripts.map { it.asString() }
    }


    fun actionModel(parameters: Parameters): Map<String, Any?> {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val executionModel = runBlocking {
            val graphStructure = graphStore.graphStructure()
            executionRepository.executionModel(documentPath, graphStructure)
        }

        return ImperativeModel.toCollection(executionModel)
    }


    fun actionStart(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val digest = runBlocking {
            val graphStructure = graphStore
                    .graphStructure()
                    .filter(AutoConventions.serverAllowed)

            executionRepository.start(
                documentPath, graphStructure)
        }

        return digest.asString()
    }


    fun actionReturn(parameters: Parameters): String {
        val hostDocumentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramHostDocumentPath, DocumentPath::parse)

        val digest = runBlocking {
            val graphStructure = graphStore
                    .graphStructure()
                    .filter(AutoConventions.serverAllowed)

            executionRepository.returnFrame(
                hostDocumentPath, graphStructure)
        }

        return digest.asString()
    }


    fun actionReset(parameters: Parameters) {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        runBlocking {
            executionRepository.reset(documentPath)
        }
    }


    fun actionPerform(parameters: Parameters): Map<String, Any?> {
        val hostDocumentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramHostDocumentPath, DocumentPath::parse)

        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val execution: ImperativeResponse = runBlocking {
            val graphStructure = graphStore.graphStructure()
            executionRepository.execute(
                hostDocumentPath, objectLocation, graphStructure)
        }

        return execution.toCollection()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun actionDetached(
        parameters: Parameters,
        body: ImmutableByteArray?
    ): Map<String, Any?> {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val detachedParams = mutableMapOf<String, List<String>>()
        for (e in parameters.entries()) {
            if (e.key == CommonRestApi.paramDocumentPath ||
                    e.key == CommonRestApi.paramObjectPath
            ) {
                continue
            }
            detachedParams[e.key] = e.value
        }

        val detachedRequest = ExecutionRequest(
            RequestParams(detachedParams), body)

        val execution: ExecutionResult = runBlocking {
            detachedExecutor.execute(
                objectLocation, detachedRequest)
        }

        return execution.toJsonCollection()
    }


    fun actionDetachedDownload(
        parameters: Parameters,
        body: ImmutableByteArray?
    ): ExecutionDownloadResult {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val params = mutableMapOf<String, List<String>>()
        for (e in parameters.entries()) {
            if (e.key == CommonRestApi.paramDocumentPath ||
                    e.key == CommonRestApi.paramObjectPath) {
                continue
            }
            params[e.key] = e.value
        }

        val detachedRequest = ExecutionRequest(RequestParams(params), body)

        val execution: ExecutionDownloadResult = runBlocking {
            detachedExecutor.executeDownload(
                objectLocation, detachedRequest)
        }

        return execution
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun execModel(parameters: Parameters): Map<String, Any?> {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath? = parameters.getParamOrNull(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val visualDataflowModel = runBlocking {
            visualDataflowRepository.get(documentPath)
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

        return result
    }


    fun execReset(parameters: Parameters): Map<String, Any> {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val visualDataflowModel = runBlocking {
            visualDataflowRepository.reset(documentPath)
        }

        return VisualDataflowModel.toJsonCollection(visualDataflowModel)
    }


    fun execPerform(parameters: Parameters): Map<String, Any?> {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val transition: VisualVertexTransition = runBlocking {
            visualDataflowRepository.execute(documentPath, objectLocation)
        }

        return VisualVertexTransition.toCollection(transition)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun taskSubmit(parameters: Parameters): Map<String, Any?> {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val params = mutableMapOf<String, List<String>>()
        for (e in parameters.entries()) {
            if (e.key == CommonRestApi.paramDocumentPath ||
                e.key == CommonRestApi.paramObjectPath) {
                continue
            }
            params[e.key] = e.value
        }

        val detachedRequest = ExecutionRequest(RequestParams(params), null)

        val execution: TaskModel = runBlocking {
            modelTaskRepository.submit(
                objectLocation,
                detachedRequest)
        }

        return execution.toJsonCollection()
    }


    fun taskQuery(parameters: Parameters): Map<String, Any?>? {
        val taskId: TaskId = parameters
            .getParam(CommonRestApi.paramTaskId) { TaskId(it) }

        val model: TaskModel = runBlocking {
            modelTaskRepository.query(taskId)
        }
            ?: return null

        return model.toJsonCollection()
    }


    fun taskCancel(parameters: Parameters): Map<String, Any?>? {
        val taskId: TaskId = parameters
            .getParam(CommonRestApi.paramTaskId) { TaskId(it) }

        val model: TaskModel = runBlocking {
            modelTaskRepository.cancel(taskId)
        }
            ?: return null

        return model.toJsonCollection()
    }


    fun taskLookup(parameters: Parameters): List<String> {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val tasks: Set<TaskId> = runBlocking {
            modelTaskRepository.lookupActive(objectLocation)
        }

        return tasks.map { it.identifier }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun logicStatus(): Map<String, Any> {
        return serverLogicController.status().toCollection()
    }


    fun logicStart(parameters: Parameters): String? {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val graphDefinitionAttempt = runBlocking {
            graphStore.graphDefinition()
        }

        val logicRunId = runBlocking {
            serverLogicController.start(objectLocation, graphDefinitionAttempt)
        }
            ?: return null

        val response = runBlocking {
            serverLogicController.continueOrStart(logicRunId, graphDefinitionAttempt)
        }

        if (response != LogicRunResponse.Submitted) {
            return null
        }

        return logicRunId.value
    }


    fun logicRequest(parameters: Parameters): Map<String, Any?> {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val executionId: LogicExecutionId = parameters.getParam(CommonRestApi.paramExecutionId) {
            value -> LogicExecutionId(value)
        }

        val params = mutableMapOf<String, List<String>>()
        for (e in parameters.entries()) {
            if (e.key == CommonRestApi.paramRunId ||
                e.key == CommonRestApi.paramExecutionId) {
                continue
            }
            params[e.key] = e.value
        }

        val request = ExecutionRequest(RequestParams(params), null)

        val result: ExecutionResult = runBlocking {
            serverLogicController.request(
                runId,
                executionId,
                request)
        }

        return result.toJsonCollection()
    }


    fun logicCancel(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val response = runBlocking {
            serverLogicController.cancel(runId)
        }

        return response.name
    }


    fun logicRun(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val response = runBlocking {
            serverLogicController.continueOrStart(runId)
        }

        return response.name
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun <T> Parameters.getParam(
        parameterName: String,
        parser: (String) -> T
    ): T {
        val queryParamValues: List<String>? = getAll(parameterName)
        require(! queryParamValues.isNullOrEmpty()) { "'$parameterName' required" }
        require(queryParamValues.size == 1) { "Single '$parameterName' expected: $queryParamValues" }
        return parser(queryParamValues.single())
    }


    private fun <T> Parameters.getParamList(
        parameterName: String,
        parser: (String) -> T
    ): List<T> {
        val queryParamValues: List<String> = getAll(parameterName)
            ?: return listOf()
        return queryParamValues.map(parser)
    }


    private fun <T> Parameters.getParamOrNull(
        parameterName: String,
        parser: (String) -> T
    ): T? {
        val queryParamValues: List<String> = getAll(parameterName)
            ?: return null

        require(queryParamValues.isNotEmpty()) { "'$parameterName' required" }
        require(queryParamValues.size == 1) { "Single '$parameterName' expected: $queryParamValues" }

        return parser(queryParamValues.single())
    }
}