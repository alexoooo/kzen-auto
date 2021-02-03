package tech.kzen.auto.client.service.rest

import tech.kzen.auto.client.util.*
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResponse
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.lib.client.ClientJsonUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.resource.ResourceListing
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.model.structure.scan.DocumentScan
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.platform.collect.toPersistentMap
import kotlin.js.Json


class ClientRestApi(
        private val baseUrl: String
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun scanNotation(): NotationScan {
        val scanText = get(
                CommonRestApi.scan,
                CommonRestApi.paramFresh to true.toString())

        val scanJson = JSON.parse<Json>(scanText)
        val scanMap = ClientJsonUtils.toMap(scanJson)

        val builder = mutableMapOf<DocumentPath, DocumentScan>()

        for ((key, value) in scanMap) {
            @Suppress("UNCHECKED_CAST")
            val valueMap = value as Map<String, Any>

            val documentDigest = valueMap["documentDigest"] as String

            @Suppress("UNCHECKED_CAST")
            val resources = valueMap["resources"] as? Map<String, String>

            builder[DocumentPath.parse(key)] = DocumentScan(
                    Digest.parse(documentDigest),
                    resources?.let {
                        ResourceListing(
                                it.map {e ->
                                    ResourcePath.parse(e.key) to Digest.parse(e.value)
                                }.toMap().toPersistentMap()
                        )
                    })
        }

        return NotationScan(DocumentPathMap(builder.toPersistentMap()))
    }


    suspend fun readNotation(location: DocumentPath): String {
        @Suppress("UNUSED_VARIABLE")
        val response = get(CommonRestApi.notationPrefix + location.asRelativeFile())

        return response
    }


    suspend fun readResource(location: ResourceLocation): ImmutableByteArray {
        @Suppress("UNUSED_VARIABLE")
        val response = getBytes(
                CommonRestApi.resource,
                CommonRestApi.paramDocumentPath to location.documentPath.asString(),
                CommonRestApi.paramResourcePath to location.resourcePath.asString())

        return ImmutableByteArray.wrap(response)
    }


    fun resourceUri(location: ResourceLocation): String {
        val suffix = paramSuffix(
                CommonRestApi.paramDocumentPath to location.documentPath.asString(),
                CommonRestApi.paramResourcePath to location.resourcePath.asString())
        return "$baseUrl${CommonRestApi.resource}$suffix"
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun createDocument(
        documentPath: DocumentPath,
        unparsedDocumentNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandDocumentCreate,
                CommonRestApi.paramDocumentPath to documentPath.asString(),
                CommonRestApi.paramDocumentNotation to unparsedDocumentNotation)
    }


    suspend fun deleteDocument(
            documentPath: DocumentPath
    ): Digest {
        return getDigest(
                CommonRestApi.commandDocumentDelete,
                CommonRestApi.paramDocumentPath to documentPath.asRelativeFile())
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun addObject(
        objectLocation: ObjectLocation,
        indexInDocument: PositionRelation,
        unparsedObjectNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandObjectAdd,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramPositionIndex to indexInDocument.asString(),
                CommonRestApi.paramObjectNotation to unparsedObjectNotation)
    }


    suspend fun removeObject(
            objectLocation: ObjectLocation
    ): Digest {
        return getDigest(
                CommonRestApi.commandObjectRemove,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString())
    }


    suspend fun shiftObject(
            objectLocation: ObjectLocation,
            newPositionInDocument: PositionRelation
    ): Digest {
        return getDigest(
                CommonRestApi.commandObjectShift,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramPositionIndex to newPositionInDocument.asString())
    }


    suspend fun renameObject(
            objectLocation: ObjectLocation,
            newName: ObjectName
    ): Digest {
        return getDigest(
                CommonRestApi.commandObjectRename,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramObjectName to newName.value)
    }


    suspend fun insertObjectInList(
        containingObjectLocation: ObjectLocation,
        containingList: AttributePath,
        indexInList: PositionRelation,
        objectName: ObjectName,
        positionInDocument: PositionRelation,
        unparsedObjectNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandObjectInsertInList,
                CommonRestApi.paramDocumentPath to containingObjectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to containingObjectLocation.objectPath.asString(),
                CommonRestApi.paramAttributePath to containingList.asString(),
                CommonRestApi.paramPositionIndex to indexInList.asString(),
                CommonRestApi.paramObjectName to objectName.value,
                CommonRestApi.paramSecondaryPosition to positionInDocument.asString(),
                CommonRestApi.paramObjectNotation to unparsedObjectNotation)
    }


    suspend fun removeObjectInAttribute(
            containingObjectLocation: ObjectLocation,
            attributePath: AttributePath
    ): Digest {
        return getDigest(
                CommonRestApi.commandObjectRemoveIn,
                CommonRestApi.paramDocumentPath to containingObjectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to containingObjectLocation.objectPath.asString(),
                CommonRestApi.paramAttributePath to attributePath.asString())
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun upsertAttribute(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        unparsedAttributeNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandAttributeUpsert,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramAttributeName to attributeName.value,
                CommonRestApi.paramAttributeNotation to unparsedAttributeNotation)
    }


    suspend fun updateInAttribute(
        objectLocation: ObjectLocation,
        attributePath: AttributePath,
        unparsedAttributeNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandAttributeUpdateIn,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramAttributePath to attributePath.asString(),
                CommonRestApi.paramAttributeNotation to unparsedAttributeNotation)
    }


    suspend fun insertListItemInAttribute(
        objectLocation: ObjectLocation,
        containingList: AttributePath,
        indexInList: PositionRelation,
        unparsedItemNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandAttributeInsertItemIn,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramAttributePath to containingList.asString(),
                CommonRestApi.paramPositionIndex to indexInList.asString(),
                CommonRestApi.paramAttributeNotation to unparsedItemNotation)
    }


    suspend fun insertAllListItemsInAttribute(
        objectLocation: ObjectLocation,
        containingList: AttributePath,
        indexInList: PositionRelation,
        unparsedItemNotations: List<String>
    ): Digest {
        return getDigest(
            CommonRestApi.commandAttributeInsertAllItemsIn,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
            CommonRestApi.paramAttributePath to containingList.asString(),
            CommonRestApi.paramPositionIndex to indexInList.asString(),
            * unparsedItemNotations.map { CommonRestApi.paramAttributeNotation to it }.toTypedArray())
    }


    suspend fun insertMapEntryInAttribute(
        objectLocation: ObjectLocation,
        containingMap: AttributePath,
        indexInMap: PositionRelation,
        mapKey: AttributeSegment,
        unparsedValueNotation: String,
        createAncestorsIfAbsent: Boolean
    ): Digest {
        return getDigest(
            CommonRestApi.commandAttributeInsertEntryIn,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
            CommonRestApi.paramAttributePath to containingMap.asString(),
            CommonRestApi.paramPositionIndex to indexInMap.asString(),
            CommonRestApi.paramAttributeKey to mapKey.asKey(),
            CommonRestApi.paramAttributeNotation to unparsedValueNotation,
            CommonRestApi.paramAttributeCreateContainer to createAncestorsIfAbsent.toString())
    }


    suspend fun removeInAttribute(
        objectLocation: ObjectLocation,
        attributePath: AttributePath,
        removeContainerIfEmpty: Boolean
    ): Digest {
        return getDigest(
            CommonRestApi.commandAttributeRemoveIn,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
            CommonRestApi.paramAttributePath to attributePath.asString(),
            CommonRestApi.paramAttributeCleanupContainer to removeContainerIfEmpty.toString())
    }


    suspend fun removeListItemInAttributeCommand(
        objectLocation: ObjectLocation,
        attributePath: AttributePath,
        unparsedItemNotation: String,
        removeContainerIfEmpty: Boolean
    ): Digest {
        return getDigest(
            CommonRestApi.commandAttributeRemoveItemIn,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
            CommonRestApi.paramAttributePath to attributePath.asString(),
            CommonRestApi.paramAttributeNotation to unparsedItemNotation,
            CommonRestApi.paramAttributeCleanupContainer to removeContainerIfEmpty.toString())
    }


    suspend fun removeAllListItemsInAttributeCommand(
        objectLocation: ObjectLocation,
        attributePath: AttributePath,
        unparsedItemNotations: List<String>,
        removeContainerIfEmpty: Boolean
    ): Digest {
        return getDigest(
            CommonRestApi.commandAttributeRemoveAllItemsIn,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
            CommonRestApi.paramAttributePath to attributePath.asString(),
            * unparsedItemNotations.map { CommonRestApi.paramAttributeNotation to it }.toTypedArray(),
            CommonRestApi.paramAttributeCleanupContainer to removeContainerIfEmpty.toString())
    }


    suspend fun shiftInAttribute(
        objectLocation: ObjectLocation,
        attributePath: AttributePath,
        newPosition: PositionRelation
    ): Digest {
        return getDigest(
            CommonRestApi.commandAttributeShiftIn,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
            CommonRestApi.paramAttributePath to attributePath.asString(),
            CommonRestApi.paramPositionIndex to newPosition.asString())
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun refactorName(
        objectLocation: ObjectLocation,
        newName: ObjectName
    ): Digest {
        return getDigest(
            CommonRestApi.commandRefactorObjectRename,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
            CommonRestApi.paramObjectName to newName.value)
    }


    suspend fun refactorDocumentName(
            documentPath: DocumentPath,
            documentName: DocumentName
    ): Digest {
        return getDigest(
                CommonRestApi.commandRefactorDocumentRename,
                CommonRestApi.paramDocumentPath to documentPath.asString(),
                CommonRestApi.paramDocumentName to documentName.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun addResource(
            resourceLocation: ResourceLocation,
            contents: ImmutableByteArray
    ): Digest {
        return postDigest(
                CommonRestApi.commandResourceAdd,
                contents.toByteArray(),
                CommonRestApi.paramDocumentPath to resourceLocation.documentPath.asString(),
                CommonRestApi.paramResourcePath to resourceLocation.resourcePath.asString())
    }


    suspend fun removeResource(location: ResourceLocation): Digest {
        return getDigest(
                CommonRestApi.commandResourceRemove,
                CommonRestApi.paramDocumentPath to location.documentPath.asString(),
                CommonRestApi.paramResourcePath to location.resourcePath.asString())
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun runningHosts(): List<DocumentPath> {
        val responseText = get(
                CommonRestApi.actionList)

        val responseJson = JSON.parse<Array<String>>(responseText)

        @Suppress("UNCHECKED_CAST")
        val responseCollection = ClientJsonUtils.toList(responseJson) as List<String>

        @Suppress("UNCHECKED_CAST")
        return responseCollection.map { DocumentPath.parse(it) }
    }


    suspend fun executionModel(host: DocumentPath): ImperativeModel {
        val responseText = get(
                CommonRestApi.actionModel,
                CommonRestApi.paramDocumentPath to host.asString())

        val responseJson = JSON.parse<Json>(responseText)
        val responseCollection = ClientJsonUtils.toMap(responseJson)

        @Suppress("UNCHECKED_CAST")
        return ImperativeModel.fromCollection(
                responseCollection)
    }


    // todo: should this be used?
    suspend fun startExecution(documentPath: DocumentPath): Digest {
        return getDigest(
                CommonRestApi.actionStart,
                CommonRestApi.paramDocumentPath to documentPath.asString())
    }


    suspend fun resetExecution(
            host: DocumentPath
    ) {
        get(CommonRestApi.actionReset,
            CommonRestApi.paramDocumentPath to host.asString())
    }


    suspend fun performAction(
            host: DocumentPath,
            objectLocation: ObjectLocation
    ): ImperativeResponse {
        val responseJson = getJson(
                CommonRestApi.actionPerform,
                CommonRestApi.paramHostDocumentPath to host.asString(),
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString())

        @Suppress("UNCHECKED_CAST")
        val responseCollection = ClientJsonUtils.toMap(responseJson)

        return ImperativeResponse.fromCollection(responseCollection)
    }


    suspend fun returnFrame(
            host: DocumentPath
    ) {
        get(CommonRestApi.actionReturn,
                CommonRestApi.paramHostDocumentPath to host.asString())
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun performDetached(
            objectLocation: ObjectLocation,
            vararg parameters: Pair<String, String>
    ): ExecutionResult {
        val responseJson = getJson(
                CommonRestApi.actionDetached,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                *parameters)

        @Suppress("UNCHECKED_CAST")
        val responseCollection = ClientJsonUtils.toMap(responseJson)

        return ExecutionResult.fromJsonCollection(responseCollection)
    }


    suspend fun performDetached(
            objectLocation: ObjectLocation,
            body: ByteArray,
            vararg parameters: Pair<String, String>
    ): ExecutionResult {
        val responseJson = postJson(
                CommonRestApi.actionDetached,
                body,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                *parameters)

        @Suppress("UNCHECKED_CAST")
        val responseCollection = ClientJsonUtils.toMap(responseJson)

        return ExecutionResult.fromJsonCollection(responseCollection)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun linkDetachedDownload(
        objectLocation: ObjectLocation,
        vararg parameters: Pair<String, String>
    ): String {
        return url(
            CommonRestApi.actionDetachedDownload,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
            *parameters)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun visualDataflowModel(
            host: DocumentPath
    ): VisualDataflowModel {
        val responseJson = getJson(
                CommonRestApi.execModel,
                CommonRestApi.paramDocumentPath to host.asString())

        val responseCollection = ClientJsonUtils.toMap(responseJson)

        @Suppress("UNCHECKED_CAST")
        return VisualDataflowModel.fromCollection(
                responseCollection as Map<String, Any>)
    }


    suspend fun visualVertexModel(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ): VisualVertexModel {
        val responseJson = getJson(
                CommonRestApi.execModel,
                CommonRestApi.paramDocumentPath to host.asString(),
                CommonRestApi.paramObjectPath to vertexLocation.objectPath.asString())

        val responseCollection = ClientJsonUtils.toMap(responseJson)

        return VisualVertexModel.fromCollection(
                responseCollection)
    }


    suspend fun resetDataflowExecution(
            host: DocumentPath
    ): VisualDataflowModel {
        val responseJson = getJson(
                CommonRestApi.execReset,
                CommonRestApi.paramDocumentPath to host.asString())

        @Suppress("UNCHECKED_CAST")
        val responseCollection = ClientJsonUtils.toMap(responseJson) as Map<String, Any>

        return VisualDataflowModel.fromCollection(
                responseCollection)
    }


    suspend fun execDataflow(
            objectLocation: ObjectLocation
    ): VisualVertexTransition {
        val responseJson = getJson(
                CommonRestApi.execPerform,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString())

        val responseCollection = ClientJsonUtils.toMap(responseJson)

        return VisualVertexTransition.fromCollection(responseCollection)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun taskSubmit(
        objectLocation: ObjectLocation,
        vararg parameters: Pair<String, String>
    ): TaskModel {
        val responseJson = getJson(
            CommonRestApi.taskSubmit,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
            *parameters)

        val responseCollection = ClientJsonUtils.toMap(responseJson)

        return TaskModel.fromJsonCollection(responseCollection)
    }


    suspend fun taskQuery(
        taskId: TaskId
    ): TaskModel {
        val responseJson = getJson(
            CommonRestApi.taskQuery,
            CommonRestApi.paramTaskId to taskId.identifier)

        val responseCollection = ClientJsonUtils.toMap(responseJson)

        return TaskModel.fromJsonCollection(responseCollection)
    }


    suspend fun taskCancel(
        taskId: TaskId
    ): TaskModel {
        val responseJson = getJson(
            CommonRestApi.taskCancel,
            CommonRestApi.paramTaskId to taskId.identifier)

        val responseCollection = ClientJsonUtils.toMap(responseJson)

        return TaskModel.fromJsonCollection(responseCollection)
    }


//    suspend fun taskRequest(
//        taskId: TaskId,
//        vararg parameters: Pair<String, String>
//    ): ExecutionResult {
//        val responseJson = getJson(
//            CommonRestApi.taskRequest,
//            CommonRestApi.paramTaskId to taskId.identifier,
//            *parameters)
//
//        @Suppress("UNCHECKED_CAST")
//        val responseCollection = ClientJsonUtils.toMap(responseJson)
//
//        return ExecutionResult.fromJsonCollection(responseCollection)
//    }


//    suspend fun taskRequest(
//        taskId: TaskId,
//        body: ByteArray,
//        vararg parameters: Pair<String, String>
//    ): ExecutionResult {
//        val responseJson = postJson(
//            CommonRestApi.taskRequest,
//            body,
//            CommonRestApi.paramTaskId to taskId.identifier,
//            *parameters)
//
//        @Suppress("UNCHECKED_CAST")
//        val responseCollection = ClientJsonUtils.toMap(responseJson)
//
//        return ExecutionResult.fromJsonCollection(responseCollection)
//    }


    suspend fun taskLookup(
        objectLocation: ObjectLocation
    ): Set<TaskId> {
        val responseJson = get(
            CommonRestApi.taskLookup,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString())

        val responseCollection = ClientJsonUtils.toList(JSON.parse(responseJson))

        return responseCollection.map { TaskId(it as String) }.toSet()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // TODO: change to POST to better align with HTTP semantics?
    private suspend fun getDigest(
            commandPath: String,
            vararg parameters: Pair<String, String>
    ): Digest {
        val response = get(commandPath, *parameters)
        return Digest.parse(response)
    }


    private suspend fun postDigest(
            commandPath: String,
            body: ByteArray,
            vararg parameters: Pair<String, String>
    ): Digest {
        val response = post(commandPath, body, *parameters)
        return Digest.parse(response)
    }


    private suspend fun postJson(
            commandPath: String,
            body: ByteArray,
            vararg parameters: Pair<String, String>
    ): Json {
        val response = post(commandPath, body, *parameters)
        return JSON.parse(response)
    }


    private suspend fun getJson(
            commandPath: String,
            vararg parameters: Pair<String, String>
    ): Json {
        val response = get(commandPath, *parameters)
        return JSON.parse(response)
    }


    private suspend fun get(
            commandPath: String,
            vararg parameters: Pair<String, String>
    ): String {
        return httpGet(
            url(commandPath, *parameters))
    }


    private suspend fun getBytes(
            commandPath: String,
            vararg parameters: Pair<String, String>
    ): ByteArray {
        return httpGetBytes(
            url(commandPath, *parameters))
    }


    fun url(
        commandPath: String,
        vararg parameters: Pair<String, String>
    ): String {
        val suffix = paramSuffix(*parameters)
        return "$baseUrl$commandPath$suffix"
    }


    private suspend fun post(
            commandPath: String,
            body: ByteArray,
            vararg parameters: Pair<String, String>
    ): String {
        val suffix = paramSuffix(*parameters)
        return httpPostBytes("$baseUrl$commandPath$suffix", body)
    }


    private suspend fun delete(
            commandPath: String,
            vararg parameters: Pair<String, String>
    ) {
        val suffix = paramSuffix(*parameters)
        httpDelete("$baseUrl$commandPath$suffix")
    }


    private fun paramSuffix(
            vararg parameters: Pair<String, String>
    ): String {
        return if (parameters.isEmpty()) {
            ""
        }
        else {
            "?" + parameters.joinToString("&") {
                it.first + "=" + encodeURIComponent(it.second)
            }
        }
    }
}