package tech.kzen.auto.client.service.rest

import tech.kzen.auto.client.util.httpGet
import tech.kzen.auto.client.util.httpGetBytes
import tech.kzen.auto.client.util.httpPostBytes
import tech.kzen.auto.client.util.httpPutForm
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunResponse
import tech.kzen.auto.common.paradigm.common.v1.model.LogicStatus
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResponse
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.auto.platform.encodeURIComponent
import tech.kzen.lib.client.ClientJsonUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
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
    companion object {
        private const val getSizeLimit = 1024
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun scanNotation(): NotationScan {
        val scanText = getOrPut(
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
        @Suppress("UnnecessaryVariable")
        val response = getOrPut(CommonRestApi.notationPrefix + location.asRelativeFile())

        return response
    }


    suspend fun readResource(location: ResourceLocation): ImmutableByteArray {
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
        return getOrPutDigest(
                CommonRestApi.commandDocumentCreate,
                CommonRestApi.paramDocumentPath to documentPath.asString(),
                CommonRestApi.paramDocumentNotation to unparsedDocumentNotation)
    }


    suspend fun deleteDocument(
            documentPath: DocumentPath
    ): Digest {
        return getOrPutDigest(
                CommonRestApi.commandDocumentDelete,
                CommonRestApi.paramDocumentPath to documentPath.asRelativeFile())
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun addObject(
        objectLocation: ObjectLocation,
        indexInDocument: PositionRelation,
        unparsedObjectNotation: String
    ): Digest {
        return getOrPutDigest(
                CommonRestApi.commandObjectAdd,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramPositionIndex to indexInDocument.asString(),
                CommonRestApi.paramObjectNotation to unparsedObjectNotation)
    }


    suspend fun removeObject(
            objectLocation: ObjectLocation
    ): Digest {
        return getOrPutDigest(
                CommonRestApi.commandObjectRemove,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString())
    }


    suspend fun shiftObject(
            objectLocation: ObjectLocation,
            newPositionInDocument: PositionRelation
    ): Digest {
        return getOrPutDigest(
                CommonRestApi.commandObjectShift,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramPositionIndex to newPositionInDocument.asString())
    }


    suspend fun renameObject(
            objectLocation: ObjectLocation,
            newName: ObjectName
    ): Digest {
        return getOrPutDigest(
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
        return getOrPutDigest(
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
        return getOrPutDigest(
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
        return getOrPutDigest(
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
        return getOrPutDigest(
                CommonRestApi.commandAttributeUpdateIn,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramAttributePath to attributePath.asString(),
                CommonRestApi.paramAttributeNotation to unparsedAttributeNotation)
    }


    suspend fun updateAllNestingsInAttribute(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        attributeNestings: List<AttributeNesting>,
        unparsedAttributeNotation: String
    ): Digest {
        val attributeNestingPairs = attributeNestings
            .map { CommonRestApi.paramAttributeNesting to it.asString() }

        return getOrPutDigest(
            CommonRestApi.commandAttributeUpdateAllNestingsIn,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
            CommonRestApi.paramAttributeName to attributeName.asString(),
            CommonRestApi.paramAttributeNotation to unparsedAttributeNotation,
            * attributeNestingPairs.toTypedArray())
    }


    suspend fun updateAllValuesInAttribute(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        nestingUnparsedNotations: Map<AttributeNesting, String>
    ): Digest {
        val nestingUnparsedNotationPairs =
            nestingUnparsedNotations.flatMap {
                listOf(
                    CommonRestApi.paramAttributeNesting to it.key.asString(),
                    CommonRestApi.paramAttributeNotation to it.value)
            }

        return getOrPutDigest(
            CommonRestApi.commandAttributeUpdateAllValuesIn,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
            CommonRestApi.paramAttributeName to attributeName.asString(),
            * nestingUnparsedNotationPairs.toTypedArray())
    }


    suspend fun insertListItemInAttribute(
        objectLocation: ObjectLocation,
        containingList: AttributePath,
        indexInList: PositionRelation,
        unparsedItemNotation: String
    ): Digest {
        return getOrPutDigest(
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
        return getOrPutDigest(
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
        return getOrPutDigest(
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
        return getOrPutDigest(
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
        return getOrPutDigest(
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
        return getOrPutDigest(
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
        return getOrPutDigest(
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
        return getOrPutDigest(
            CommonRestApi.commandRefactorObjectRename,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
            CommonRestApi.paramObjectName to newName.value)
    }


    suspend fun refactorDocumentName(
            documentPath: DocumentPath,
            documentName: DocumentName
    ): Digest {
        return getOrPutDigest(
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
        return getOrPutDigest(
                CommonRestApi.commandResourceRemove,
                CommonRestApi.paramDocumentPath to location.documentPath.asString(),
                CommonRestApi.paramResourcePath to location.resourcePath.asString())
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun runningHosts(): List<DocumentPath> {
        val responseText = getOrPut(
            CommonRestApi.actionList)

        val responseJson = JSON.parse<Array<String>>(responseText)

        @Suppress("UNCHECKED_CAST")
        val responseCollection = ClientJsonUtils.toList(responseJson) as List<String>

        @Suppress("UNCHECKED_CAST")
        return responseCollection.map { DocumentPath.parse(it) }
    }


    suspend fun executionModel(host: DocumentPath): ImperativeModel {
        val responseText = getOrPut(
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
        return getOrPutDigest(
                CommonRestApi.actionStart,
                CommonRestApi.paramDocumentPath to documentPath.asString())
    }


    suspend fun resetExecution(
            host: DocumentPath
    ) {
        getOrPut(CommonRestApi.actionReset,
            CommonRestApi.paramDocumentPath to host.asString())
    }


    suspend fun performAction(
            host: DocumentPath,
            objectLocation: ObjectLocation
    ): ImperativeResponse {
        val responseJson = getOrPutJson(
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
        getOrPut(CommonRestApi.actionReturn,
            CommonRestApi.paramHostDocumentPath to host.asString())
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun performDetached(
            objectLocation: ObjectLocation,
            vararg parameters: Pair<String, String>
    ): ExecutionResult {
        val responseJson = getOrPutJson(
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
        val responseJson = getOrPutJson(
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
        val responseJson = getOrPutJson(
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
        val responseJson = getOrPutJson(
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
        val responseJson = getOrPutJson(
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
        val responseJson = getOrPutJson(
            CommonRestApi.taskSubmit,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
            *parameters)

        val responseCollection = ClientJsonUtils.toMap(responseJson)

        return TaskModel.fromJsonCollection(responseCollection)
    }


    suspend fun taskQuery(
        taskId: TaskId
    ): TaskModel? {
        val responseJson = getOrPutJsonOrNull(
            CommonRestApi.taskQuery,
            CommonRestApi.paramTaskId to taskId.identifier)
            ?: return null

        val responseCollection = ClientJsonUtils.toMap(responseJson)

        return TaskModel.fromJsonCollection(responseCollection)
    }


    suspend fun taskCancel(
        taskId: TaskId
    ): TaskModel? {
        val responseJson = getOrPutJsonOrNull(
            CommonRestApi.taskCancel,
            CommonRestApi.paramTaskId to taskId.identifier)
            ?: return null

        val responseCollection = ClientJsonUtils.toMap(responseJson)

        return TaskModel.fromJsonCollection(responseCollection)
    }


    suspend fun taskLookup(
        objectLocation: ObjectLocation
    ): Set<TaskId> {
        val responseJson = getOrPut(
            CommonRestApi.taskLookup,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString())

        val responseCollection = ClientJsonUtils.toList(JSON.parse(responseJson))

        return responseCollection.map { TaskId(it as String) }.toSet()
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun logicStatus(): LogicStatus {
        val responseJson = getOrPutJson(CommonRestApi.logicStatus)

        @Suppress("UNCHECKED_CAST")
        val responseCollection = ClientJsonUtils.toMap(responseJson) as Map<String, Any>

        return LogicStatus.ofCollection(responseCollection)
    }


    suspend fun logicStart(
        objectLocation: ObjectLocation
    ): LogicRunId? {
        val response = getOrPut(
            CommonRestApi.logicStart,
            CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
            CommonRestApi.paramObjectPath to objectLocation.objectPath.asString())

        return when {
            response.isEmpty() -> null
            else -> LogicRunId(response)
        }
    }


    suspend fun logicRequest(
        runId: LogicRunId,
        executionId: LogicExecutionId,
        vararg parameters: Pair<String, String>
    ): ExecutionResult {
        val responseJson = getOrPutJson(
            CommonRestApi.logicRequest,
            CommonRestApi.paramRunId to runId.value,
            CommonRestApi.paramExecutionId to executionId.value,
            *parameters)

        @Suppress("UNCHECKED_CAST")
        val responseCollection = ClientJsonUtils.toMap(responseJson)

        return ExecutionResult.fromJsonCollection(responseCollection)
    }


    suspend fun logicCancel(
        runId: LogicRunId
    ): LogicRunResponse {
        val response = getOrPut(
            CommonRestApi.logicCancel,
            CommonRestApi.paramRunId to runId.value)

        return LogicRunResponse.valueOf(response)
    }


    suspend fun logicRun(
        runId: LogicRunId
    ): LogicRunResponse {
        val response = getOrPut(
            CommonRestApi.logicRun,
            CommonRestApi.paramRunId to runId.value)

        return LogicRunResponse.valueOf(response)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // TODO: change to POST to better align with HTTP semantics?
    private suspend fun getOrPutDigest(
            commandPath: String,
            vararg parameters: Pair<String, String>
    ): Digest {
        val response = getOrPut(commandPath, *parameters)
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


    private suspend fun getOrPutJson(
            commandPath: String,
            vararg parameters: Pair<String, String>
    ): Json {
        val response = getOrPut(commandPath, *parameters)
        return JSON.parse(response)
    }


    private suspend fun getOrPutJsonOrNull(
            commandPath: String,
            vararg parameters: Pair<String, String>
    ): Json? {
        val response = getOrPut(commandPath, *parameters)
        return when {
            response.isEmpty() -> null
            else -> JSON.parse(response)
        }
    }


    private suspend fun getOrPut(
            commandPath: String,
            vararg parameters: Pair<String, String>
    ): String {
        val getUrl = url(commandPath, *parameters)

        return when {
            getUrl.length <= getSizeLimit ->
                httpGet(getUrl)

            else ->
                httpPutForm(commandUrl(commandPath), *parameters)
        }
    }


    private suspend fun getBytes(
            commandPath: String,
            vararg parameters: Pair<String, String>
    ): ByteArray {
        return httpGetBytes(
            url(commandPath, *parameters))
    }


    private fun url(
        commandPath: String,
        vararg parameters: Pair<String, String>
    ): String {
        val prefix = commandUrl(commandPath)
        val suffix = paramSuffix(*parameters)
        return "$prefix$suffix"
    }


    private suspend fun post(
            commandPath: String,
            body: ByteArray,
            vararg parameters: Pair<String, String>
    ): String {
        val prefix = commandUrl(commandPath)
        val suffix = paramSuffix(*parameters)
        return httpPostBytes("$prefix$suffix", body)
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


    private fun commandUrl(
        commandPath: String
    ): String {
        return "$baseUrl$commandPath"
    }
}