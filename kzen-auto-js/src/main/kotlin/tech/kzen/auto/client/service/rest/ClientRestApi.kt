package tech.kzen.auto.client.service.rest

import tech.kzen.auto.client.util.encodeURIComponent
import tech.kzen.auto.client.util.httpGet
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionModel
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionResponse
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionResult
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.structure.notation.model.PositionIndex
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.IoUtils
import tech.kzen.lib.platform.client.ClientJsonUtils
import tech.kzen.lib.platform.collect.toPersistentMap
import kotlin.js.Json


class ClientRestApi(
        private val baseUrl: String
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun scanNotationPaths(): DocumentPathMap<Digest> {
        val scanText = get(CommonRestApi.scan)

        val builder = mutableMapOf<DocumentPath, Digest>()
        // NB: using transform just to iterate the Json, is there a better way to do this?
        JSON.parse<Json>(scanText) { key: String, value: Any? ->
            if (value is String) {
                builder[DocumentPath.parse(key)] = Digest.parse(value)
            }
            null
        }
        return DocumentPathMap(builder.toPersistentMap())
    }


    suspend fun readNotation(location: DocumentPath): ByteArray {
        @Suppress("UNUSED_VARIABLE")
        val response = get(CommonRestApi.notationPrefix + location.asRelativeFile())

        return IoUtils.utf8Encode(response)
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
    suspend fun executionModel(host: DocumentPath): ExecutionModel {
        val responseText = get(
                CommonRestApi.actionModel,
                CommonRestApi.paramDocumentPath to host.asString())

        val responseJson = JSON.parse<Array<Json>>(responseText)
        val responseCollection = ClientJsonUtils.toList(responseJson)

        @Suppress("UNCHECKED_CAST")
        return ExecutionModel.fromCollection(
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
    ): ExecutionResponse {
        val responseJson = getJson(
                CommonRestApi.actionPerform,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString())

        @Suppress("UNCHECKED_CAST")
        val responseCollection = ClientJsonUtils.toMap(responseJson)

//        val status = responseJson[CommonRestApi.fieldStatus] as String
//        val digest = responseJson[CommonRestApi.fieldDigest] as String

        return ExecutionResponse.fromCollection(responseCollection)
    }


    suspend fun performDetached(
            objectLocation: ObjectLocation
    ): ExecutionResult {
        val responseJson = getJson(
                CommonRestApi.actionDetached,
                CommonRestApi.paramDocumentPath to objectLocation.documentPath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString())

        @Suppress("UNCHECKED_CAST")
        val responseCollection = ClientJsonUtils.toMap(responseJson)

        return ExecutionResult.fromCollection(responseCollection)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun getDigest(
            commandPath: String,
            vararg parameters: Pair<String, String>
    ): Digest {
        val response = get(commandPath, *parameters)
        return Digest.parse(response)
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
        val suffix =
                if (parameters.isEmpty()) {
                    ""
                }
                else {
                    "?" + parameters.joinToString("&") {
                        it.first + "=" + encodeURIComponent(it.second)
                    }
                }

        return httpGet("$baseUrl$commandPath$suffix")
    }
}