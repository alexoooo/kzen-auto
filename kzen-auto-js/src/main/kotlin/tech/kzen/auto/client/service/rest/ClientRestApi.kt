package tech.kzen.auto.client.service.rest

import tech.kzen.auto.client.util.*
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResponse
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.PositionIndex
import tech.kzen.lib.common.model.structure.resource.ResourceListing
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.model.structure.scan.DocumentScan
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.platform.client.ClientJsonUtils
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
            deparsedDocumentNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandDocumentCreate,
                CommonRestApi.paramDocumentPath to documentPath.asString(),
                CommonRestApi.paramDocumentNotation to deparsedDocumentNotation)
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
            indexInDocument: PositionIndex,
            deparsedObjectNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandObjectAdd,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramPositionIndex to indexInDocument.asString(),
                CommonRestApi.paramObjectNotation to deparsedObjectNotation)
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
            newPositionInDocument: PositionIndex
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
            indexInList: PositionIndex,
            objectName: ObjectName,
            positionInDocument: PositionIndex,
            deparsedObjectNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandObjectInsertInList,
                CommonRestApi.paramDocumentPath to containingObjectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to containingObjectLocation.objectPath.asString(),
                CommonRestApi.paramAttributePath to containingList.asString(),
                CommonRestApi.paramPositionIndex to indexInList.asString(),
                CommonRestApi.paramObjectName to objectName.value,
                CommonRestApi.paramSecondaryPosition to positionInDocument.asString(),
                CommonRestApi.paramObjectNotation to deparsedObjectNotation)
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
            deparsedAttributeNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandAttributeUpsert,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramAttributeName to attributeName.value,
                CommonRestApi.paramAttributeNotation to deparsedAttributeNotation)
    }


    suspend fun updateInAttribute(
            objectLocation: ObjectLocation,
            attributePath: AttributePath,
            deparsedAttributeNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandAttributeUpdateIn,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramAttributePath to attributePath.asString(),
                CommonRestApi.paramAttributeNotation to deparsedAttributeNotation)
    }


    suspend fun insertListItemInAttribute(
            objectLocation: ObjectLocation,
            containingList: AttributePath,
            indexInList: PositionIndex,
            deparsedItemNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandAttributeInsertItemIn,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramAttributePath to containingList.asString(),
                CommonRestApi.paramPositionIndex to indexInList.asString(),
                CommonRestApi.paramAttributeNotation to deparsedItemNotation)
    }


    suspend fun insertMapEntryInAttribute(
            objectLocation: ObjectLocation,
            containingMap: AttributePath,
            indexInMap: PositionIndex,
            mapKey: AttributeSegment,
            deparsedValueNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandAttributeInsertEntryIn,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramAttributePath to containingMap.asString(),
                CommonRestApi.paramPositionIndex to indexInMap.asString(),
                CommonRestApi.paramAttributeKey to mapKey.asKey(),
                CommonRestApi.paramAttributeNotation to deparsedValueNotation)
    }


    suspend fun removeInAttribute(
            objectLocation: ObjectLocation,
            attributePath: AttributePath
    ): Digest {
        return getDigest(
                CommonRestApi.commandAttributeRemoveIn,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramAttributePath to attributePath.asString())
    }


    suspend fun shiftInAttribute(
            objectLocation: ObjectLocation,
            attributePath: AttributePath,
            newPosition: PositionIndex
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
    suspend fun executionModel(host: DocumentPath): ImperativeModel {
        val responseText = get(
                CommonRestApi.actionModel,
                CommonRestApi.paramDocumentPath to host.asString())

        val responseJson = JSON.parse<Array<Json>>(responseText)
        val responseCollection = ClientJsonUtils.toList(responseJson)

        @Suppress("UNCHECKED_CAST")
        return ImperativeModel.fromCollection(
                responseCollection as List<Map<String, Any>>)
    }


    suspend fun startExecution(documentPath: DocumentPath): Digest {
        return getDigest(
                CommonRestApi.actionStart,
                CommonRestApi.paramDocumentPath to documentPath.asString())
    }


    suspend fun resetExecution(
            host: DocumentPath
    ): Digest {
        return getDigest(
                CommonRestApi.actionReset,
                CommonRestApi.paramDocumentPath to host.asString())
    }


    suspend fun performAction(
            objectLocation: ObjectLocation
    ): ImperativeResponse {
        val responseJson = getJson(
                CommonRestApi.actionPerform,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString())

        @Suppress("UNCHECKED_CAST")
        val responseCollection = ClientJsonUtils.toMap(responseJson)

        return ImperativeResponse.fromCollection(responseCollection)
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

        return ExecutionResult.fromCollection(responseCollection)
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

        return ExecutionResult.fromCollection(responseCollection)
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

        @Suppress("UNCHECKED_CAST")
        val responseCollection = ClientJsonUtils.toMap(responseJson)

        return VisualVertexTransition.fromCollection(responseCollection)
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
        val suffix = paramSuffix(*parameters)
        return httpGet("$baseUrl$commandPath$suffix")
    }


    private suspend fun getBytes(
            commandPath: String,
            vararg parameters: Pair<String, String>
    ): ByteArray {
        val suffix = paramSuffix(*parameters)
        return httpGetBytes("$baseUrl$commandPath$suffix")
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