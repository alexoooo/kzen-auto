package tech.kzen.auto.server.api

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.auto.server.exec.RunEngineLogicTrace
import tech.kzen.auto.server.objects.job.service.JobWorkPool
import tech.kzen.auto.server.objects.report.exec.output.flat.IndexedCsvTable
import tech.kzen.auto.server.objects.report.service.FileListingAction
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.auto.common.util.storage.StorageAreaInfo
import tech.kzen.auto.common.util.storage.StorageBundleInfo
import tech.kzen.auto.server.service.exec.ModelDetachedExecutor
import tech.kzen.auto.server.service.exec.ModelTaskRepository
import tech.kzen.auto.server.service.impl.ServerLogicController
import tech.kzen.auto.server.service.storage.ManagedStorageRegistry
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.exec.engine.StepMode
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunResponse
import tech.kzen.lib.common.exec.logic.run.model.LogicStatus
import tech.kzen.lib.common.exec.task.model.TaskId
import tech.kzen.lib.common.exec.task.model.TaskModel
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.store.DirectGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.platform.DateTimeUtils
import java.net.URI
import java.nio.file.Files


class RestHandler(
    private val notationMedia: NotationMedia,
    private val yamlNotationParser: NotationParser,
    private val graphStore: DirectGraphStore,
    private val detachedExecutor: ModelDetachedExecutor,
    private val modelTaskRepository: ModelTaskRepository,
    private val serverLogicController: ServerLogicController,
    private val runEngineLogicTrace: RunEngineLogicTrace,
    private val objectStableMapper: ObjectStableMapper,
    private val fileListingAction: FileListingAction,
    private val jobWorkPool: JobWorkPool,
    private val managedStorageRegistry: ManagedStorageRegistry
) {
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

        for (e in documentTree.documents.map) {
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


    fun notationBatch(parameters: Parameters): Map<String, String> {
        val rawPaths = parameters.getAll(CommonRestApi.paramDocumentPath)
            ?: return emptyMap()

        val paths = rawPaths.map { DocumentPath.parse(it) }

        val documents = runBlocking {
            notationMedia.readDocuments(paths)
        }

        return paths.associate { path ->
            path.asRelativeFile() to (documents[path]
                ?: throw IllegalStateException("Missing document: $path"))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun createDocument(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        // the path form (trailing slash) distinguishes a pure folder from a document — route accordingly
        if (documentPath.folder) {
            return applyCommand(CreateFolderCommand(documentPath)).asString()
        }

        val documentBody = parameters.getParam(CommonRestApi.paramDocumentNotation) {
            yamlNotationParser.parseDocumentObjects(it)
        }

        val command = CreateDocumentCommand(documentPath, documentBody)
        return applyCommand(command).asString()
    }


    fun deleteDocument(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val command =
            if (documentPath.folder) {
                DeleteFolderCommand(documentPath)
            }
            else {
                DeleteDocumentCommand(documentPath)
            }
        return applyCommand(command).asString()
    }


    fun setDocumentObjects(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val documentObjectNotation = parameters.getParam(
            CommonRestApi.paramRawObjectsYaml, yamlNotationParser::parseDocumentObjects)

        val command = SetDocumentObjectsCommand(documentPath, documentObjectNotation)
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


    fun shiftObjectTree(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val indexInDocument: PositionRelation = parameters.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

        val command = ShiftObjectTreeCommand(
            ObjectLocation(documentPath, objectPath),
            indexInDocument)

        return applyCommand(command).asString()
    }


    fun relocateObjectTree(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val newObjectNesting: ObjectNesting = parameters.getParam(
            CommonRestApi.paramObjectNesting, ObjectNesting::parse)

        val indexInDocument: PositionRelation = parameters.getParam(
            CommonRestApi.paramPositionIndex, PositionRelation::parse)

        val command = RelocateObjectTreeRefactorCommand(
            ObjectLocation(documentPath, objectPath),
            newObjectNesting,
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


    fun addObjectAtAttribute(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val containingObjectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val containingAttirute: AttributeName = parameters.getParam(
            CommonRestApi.paramAttributeName, AttributeName::parse)

        val objectName: ObjectName = parameters.getParam(
            CommonRestApi.paramObjectName, ::ObjectName)

        val positionInDocument: PositionRelation = parameters.getParam(
            CommonRestApi.paramSecondaryPosition, PositionRelation::parse)

        val objectNotation: ObjectNotation = parameters.getParam(
            CommonRestApi.paramObjectNotation, yamlNotationParser::parseObject)

        val command = AddObjectAtAttributeCommand(
            ObjectLocation(documentPath, containingObjectPath),
            containingAttirute,
            objectName,
            positionInDocument,
            objectNotation)

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

        // the path form (trailing slash) distinguishes a pure folder from a document — route accordingly
        val command =
            if (documentPath.folder) {
                RenameFolderRefactorCommand(documentPath, newName)
            }
            else {
                RenameDocumentRefactorCommand(documentPath, newName)
            }

        return applyCommand(command).asString()
    }


    fun refactorMove(parameters: Parameters): String {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val newNesting: DocumentNesting = parameters.getParam(
            CommonRestApi.paramDocumentNesting, DocumentNesting::parse)

        // the path form (trailing slash) distinguishes a pure folder from a document — route accordingly
        val command =
            if (documentPath.folder) {
                MoveFolderRefactorCommand(documentPath, newNesting)
            }
            else {
                MoveDocumentRefactorCommand(documentPath, newNesting)
            }

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
            graphStore.apply(command)
            graphStore.digest()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun actionList(): List<String> {
//        val activeScripts = executionRepository.activeScripts()
//        return activeScripts.map { it.asString() }
//    }
//
//
//    fun actionModel(parameters: Parameters): Map<String, Any?> {
//        val documentPath: DocumentPath = parameters.getParam(
//            CommonRestApi.paramDocumentPath, DocumentPath::parse)
//
//        val executionModel = runBlocking {
//            val graphStructure = graphStore.graphStructure()
//            executionRepository.executionModel(documentPath, graphStructure)
//        }
//
//        return ImperativeModel.toCollection(executionModel)
//    }
//
//
//    fun actionStart(parameters: Parameters): String {
//        val documentPath: DocumentPath = parameters.getParam(
//            CommonRestApi.paramDocumentPath, DocumentPath::parse)
//
//        val digest = runBlocking {
//            val graphStructure = graphStore
//                    .graphStructure()
//                    .filter(AutoConventions.serverAllowed)
//
//            executionRepository.start(
//                documentPath, graphStructure)
//        }
//
//        return digest.asString()
//    }
//
//
//    fun actionReturn(parameters: Parameters): String {
//        val hostDocumentPath: DocumentPath = parameters.getParam(
//            CommonRestApi.paramHostDocumentPath, DocumentPath::parse)
//
//        val digest = runBlocking {
//            val graphStructure = graphStore
//                    .graphStructure()
//                    .filter(AutoConventions.serverAllowed)
//
//            executionRepository.returnFrame(
//                hostDocumentPath, graphStructure)
//        }
//
//        return digest.asString()
//    }
//
//
//    fun actionReset(parameters: Parameters) {
//        val documentPath: DocumentPath = parameters.getParam(
//            CommonRestApi.paramDocumentPath, DocumentPath::parse)
//
//        runBlocking {
//            executionRepository.reset(documentPath)
//        }
//    }
//
//
//    fun actionPerform(parameters: Parameters): Map<String, Any?> {
//        val hostDocumentPath: DocumentPath = parameters.getParam(
//            CommonRestApi.paramHostDocumentPath, DocumentPath::parse)
//
//        val documentPath: DocumentPath = parameters.getParam(
//            CommonRestApi.paramDocumentPath, DocumentPath::parse)
//
//        val objectPath: ObjectPath = parameters.getParam(
//            CommonRestApi.paramObjectPath, ObjectPath::parse)
//
//        val objectLocation = ObjectLocation(documentPath, objectPath)
//
//        val execution: ImperativeResponse = runBlocking {
//            val graphStructure = graphStore.graphStructure()
//            executionRepository.execute(
//                hostDocumentPath, objectLocation, graphStructure)
//        }
//
//        return execution.toCollection()
//    }


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
    // Document-agnostic directory listing (GET /file-listing?directory=...&filter=...): lists the immediate
    // children (files + subdirectories) of `directory` matching `filter` via the reused FileListingAction, each
    // as its DataLocationInfo collection. The Job MultiFileInputEditor browses input files with it. A file
    // `directory` yields just that file; a missing / non-directory path yields an empty list (FileListingAction).
    fun fileListing(parameters: Parameters): List<DataLocationInfo> {
        val directory: String = parameters.getParam(CommonRestApi.paramDirectory) { it }
        val filter: String = parameters.getParamOrNull(CommonRestApi.paramFilter) { it } ?: ""

        val listing = runBlocking {
            fileListingAction.scanInfo(DataLocation.of(directory), filter)
        }

        return listing
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun storageSummary(): List<StorageAreaInfo> {
        return managedStorageRegistry.areas().map { area ->
            val bundles = area.bundles()
            StorageAreaInfo(
                area.id,
                area.displayName,
                area.description,
                bundles.sumOf { it.sizeBytes },
                bundles.size,
                area.deletable,
                area.budgetBytes
            )
        }
    }


    fun storageBundleList(parameters: Parameters): List<StorageBundleInfo> {
        val areaId: String = parameters.getParam(CommonRestApi.paramStorageArea) { it }
        val area = managedStorageRegistry.find(areaId)
            ?: error("Unknown storage area: $areaId")

        return area
            .bundles()
            .sortedByDescending { it.sizeBytes }
            .map {
                StorageBundleInfo(it.key, it.displayName, it.sizeBytes, it.lastModifiedMillis, it.active)
            }
    }


    /**
     * @return error message, or empty on success
     */
    fun storageBundleDelete(parameters: Parameters): String {
        val areaId: String = parameters.getParam(CommonRestApi.paramStorageArea) { it }
        val bundleKey: String = parameters.getParam(CommonRestApi.paramStorageBundle) { it }

        val area = managedStorageRegistry.find(areaId)
            ?: return "Unknown storage area: $areaId"

        return area.deleteBundle(bundleKey) ?: ""
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun taskSubmit(parameters: Parameters): TaskModel {
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

        return execution
    }


    fun taskQuery(parameters: Parameters): TaskModel? {
        val taskId: TaskId = parameters
            .getParam(CommonRestApi.paramTaskId) { TaskId(it) }

        return runBlocking {
            modelTaskRepository.query(taskId)
        }
    }


    fun taskCancel(parameters: Parameters): TaskModel? {
        val taskId: TaskId = parameters
            .getParam(CommonRestApi.paramTaskId) { TaskId(it) }

        return runBlocking {
            modelTaskRepository.cancel(taskId)
        }
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
    // Typed LogicStatus for both transports: GET /logic/status serializes it via respondJson, and the
    // /logic/events SSE route encodes it with the same serverJson (byte-identical, so pushed and polled
    // statuses parse through one client path). status() is @Synchronized — build the object here, then
    // encode OUTSIDE the monitor (the SSE route does exactly that).
    fun logicStatus(): LogicStatus {
        return serverLogicController.status()
    }


    // Subscribe to "the logic status may have changed". See ServerLogicController.observeStatus for the
    // contract — in particular, the listener runs on an engine thread on the hot path and must only hand off.
    fun observeLogicStatus(listener: () -> Unit): AutoCloseable {
        return serverLogicController.observeStatus(listener)
    }


    fun logicStart(parameters: Parameters, paused: Boolean): String? {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

        val pauseOnError: Boolean = parameters
            .getAll(CommonRestApi.paramPauseOnError)
            ?.singleOrNull()
            ?.toBoolean()
            ?: false

        // The mode of the first step of a paused (stepping) start — Into (plain "Start Stepping") unless the
        // client asks to start stepping OVER (run a sub-Logic entered on the first boundary to completion).
        val stepMode: StepMode = parameters
            .getAll(CommonRestApi.paramStepMode)
            ?.singleOrNull()
            ?.let { StepMode.valueOf(it) }
            ?: StepMode.Into

        val graphDefinitionAttempt = runBlocking {
            graphStore.graphDefinition()
        }

        val logicRunId = runBlocking {
            serverLogicController.start(objectLocation, graphDefinitionAttempt, pauseOnError)
        }
            ?: return null

        // Start-time breakpoints ride the start request and are set before the drive below launches the
        // engine — race-free (a follow-up PUT after startRun could miss the earliest steps).
        val breakpoints: List<ObjectLocation> = parameters.getParamList(
            CommonRestApi.paramBreakpoint, ObjectLocation::parse)
        if (breakpoints.isNotEmpty()) {
            serverLogicController.setBreakpoints(logicRunId, breakpoints)
        }

        val response = runBlocking {
            if (paused) {
                // Atomic launch-park-then-first-step (see ServerLogicController.startStep) — NOT a separate
                // pause() + step(), which races on the run flags ("Can't step, already running").
                serverLogicController.startStep(logicRunId, stepMode)
            }
            else {
                serverLogicController.continueOrStart(logicRunId, graphDefinitionAttempt)
            }
        }

        if (response != LogicRunResponse.Submitted) {
            return null
        }

        return logicRunId.value
    }


    // Hash-addressed screenshot blob. Returns null (→ 404) for a missing param, a non-retained run, or an
    // unknown hash — the client thumbnail then falls back to blank, same as any cleared trace.
    fun logicTraceBinary(parameters: Parameters): ByteArray? {
        val runIdValue = parameters[CommonRestApi.paramRunId]
            ?: return null
        val hash = parameters[CommonRestApi.paramContentHash]
            ?: return null
        return runEngineLogicTrace.lookupBinary(LogicRunId(runIdValue), hash)
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


    // Streaming download of a Job Explore Worker's PERSISTED result as table.csv — the Job analogue of Report's
    // detached download (RestHandler.actionDetachedDownload). The Worker's IndexedCsvTable lives in a per-Worker
    // output dir keyed on its NOTATION identity (JobWorkPool.workerOutputDir), which SURVIVES the run settling
    // (last-run-wins), so this resolves it straight from path + object with NO live run — letting the report be
    // downloaded after the run ends. The object path both resolves the Worker's dir and names the file.
    fun jobDownload(parameters: Parameters): ExecutionDownloadResult {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val workerLocation = ObjectLocation(documentPath, objectPath)

        val outputDir = jobWorkPool.workerOutputDir(workerLocation)
        val tablePath = outputDir.resolve(IndexedCsvTable.tableFile)
        if (! Files.exists(tablePath)) {
            error("No downloadable result: $workerLocation")
        }

        val filenamePrefix = FormatUtils.sanitizeFilename(objectPath.name.value)
        val filename = filenamePrefix + "_" + DateTimeUtils.filenameTimestamp() + ".csv"

        return ExecutionDownloadResult(
            IndexedCsvTable.downloadCsvOffline(outputDir),
            filename,
            "text/csv")
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


    fun logicPause(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val response = runBlocking {
            serverLogicController.pause(runId)
        }

        return response.name
    }


    fun logicContinueRun(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val response = runBlocking {
            serverLogicController.continueOrStart(runId)
        }

        return response.name
    }


    fun logicSetBreakpoints(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val breakpoints: List<ObjectLocation> = parameters.getParamList(
            CommonRestApi.paramBreakpoint, ObjectLocation::parse)

        val response = serverLogicController.setBreakpoints(runId, breakpoints)

        return response.name
    }


    fun logicSetPauseOnError(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val pauseOnError: Boolean = parameters
            .getAll(CommonRestApi.paramPauseOnError)
            ?.singleOrNull()
            ?.toBoolean()
            ?: false

        val response = runBlocking {
            serverLogicController.setPauseOnError(runId, pauseOnError)
        }

        return response.name
    }


    fun logicContinueStep(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val response = runBlocking {
            serverLogicController.step(runId)
        }

        return response.name
    }


    fun logicStepOver(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val response = runBlocking {
            serverLogicController.stepOver(runId)
        }

        return response.name
    }


    fun logicStepOut(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val response = runBlocking {
            serverLogicController.stepOut(runId)
        }

        return response.name
    }


    fun logicMoveTo(parameters: Parameters): String {
        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
            value -> LogicRunId(value)
        }

        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)
        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)
        val target = ObjectLocation(documentPath, objectPath)

        val response = runBlocking {
            serverLogicController.moveTo(runId, target)
        }

        return response.name
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun objectStableMapperSnapshot(): Map<String, String> {
        val snapshot = objectStableMapper.snapshot()
        return snapshot.entries.associate { (id, location) ->
            id.value to location.asString()
        }
    }


//    fun logicStartStep(parameters: Parameters): String {
//        val runId: LogicRunId = parameters.getParam(CommonRestApi.paramRunId) {
//            value -> LogicRunId(value)
//        }
//
//        val response = runBlocking {
//            serverLogicController.step(runId)
//        }
//
//        return response.name
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun <T> Parameters.getParam(
        parameterName: String,
        parser: (String) -> T
    ): T {
        val queryParamValues: List<String>? = getAll(parameterName)
        require(!queryParamValues.isNullOrEmpty()) { "'$parameterName' required" }
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