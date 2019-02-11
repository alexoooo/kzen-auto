package tech.kzen.auto.client.service

import tech.kzen.auto.client.util.encodeURIComponent
import tech.kzen.auto.client.util.httpGet
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.exec.codec.ExecutionModelEncoding
import tech.kzen.auto.common.exec.codec.ExecutionResultResponse
import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.notation.model.PositionIndex
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.IoUtils
import tech.kzen.lib.platform.client.ClientJsonUtils
import kotlin.js.Json


class ClientRestApi(
        private val baseUrl: String
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun scanNotationPaths(): BundleTree<Digest> {
        val scanText = httpGet("$baseUrl${CommonRestApi.scan}")

        val builder = mutableMapOf<BundlePath, Digest>()
        // NB: using transform just to iterate the Json, is there a better way to do this?
        JSON.parse<Json>(scanText) { key: String, value: Any? ->
            if (value is String) {
                builder[BundlePath.parse(key)] = Digest.parse(value)
            }
            null
        }
        return BundleTree(builder)
    }


    suspend fun readNotation(location: BundlePath): ByteArray {
        @Suppress("UNUSED_VARIABLE")
        val response = httpGet("$baseUrl${CommonRestApi.notationPrefix}" +
                location.asRelativeFile())

        return IoUtils.utf8Encode(response)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun createBundle(
            projectPath: BundlePath
    ): Digest {
        return getDigest(
                CommonRestApi.commandBundleCreate,
                CommonRestApi.paramBundlePath to projectPath.asString())
    }


    suspend fun deleteBundle(
            projectPath: BundlePath
    ): Digest {
        return getDigest(
                CommonRestApi.commandBundleDelete,
                CommonRestApi.paramBundlePath to projectPath.asRelativeFile())
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun addObject(
            objectLocation: ObjectLocation,
            indexInBundle: PositionIndex,
            deparsedObjectNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandObjectAdd,
                CommonRestApi.paramBundlePath to objectLocation.bundlePath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramPositionIndex to indexInBundle.asString(),
                CommonRestApi.paramObjectNotation to deparsedObjectNotation)
    }


    suspend fun removeObject(
            objectLocation: ObjectLocation
    ): Digest {
        return getDigest(
                CommonRestApi.commandObjectRemove,
                CommonRestApi.paramBundlePath to objectLocation.bundlePath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString())
    }


    suspend fun shiftObject(
            objectLocation: ObjectLocation,
            newPositionInBundle: PositionIndex
    ): Digest {
        return getDigest(
                CommonRestApi.commandObjectShift,
                CommonRestApi.paramBundlePath to objectLocation.bundlePath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramPositionIndex to newPositionInBundle.asString())
    }


    suspend fun renameObject(
            objectLocation: ObjectLocation,
            newName: ObjectName
    ): Digest {
        return getDigest(
                CommonRestApi.commandObjectRename,
                CommonRestApi.paramBundlePath to objectLocation.bundlePath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramObjectName to newName.value)
    }


    suspend fun insertObjectInList(
            containingObjectLocation: ObjectLocation,
            containingList: AttributePath,
            indexInList: PositionIndex,
            objectName: ObjectName,
            positionInBundle: PositionIndex,
            deparsedObjectNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandObjectInsert,
                CommonRestApi.paramBundlePath to containingObjectLocation.bundlePath.asString(),
                CommonRestApi.paramObjectPath to containingObjectLocation.objectPath.asString(),
                CommonRestApi.paramAttributePath to containingList.asString(),
                CommonRestApi.paramPositionIndex to indexInList.asString(),
                CommonRestApi.paramObjectName to objectName.value,
                CommonRestApi.paramSecondaryPosition to positionInBundle.asString(),
                CommonRestApi.paramObjectNotation to deparsedObjectNotation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun upsertAttribute(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            deparsedAttributeNotation: String
    ): Digest {
        return getDigest(
                CommonRestApi.commandAttributeUpsert,
                CommonRestApi.paramBundlePath to objectLocation.bundlePath.asString(),
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
                CommonRestApi.paramBundlePath to objectLocation.bundlePath.asString(),
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
                CommonRestApi.paramBundlePath to objectLocation.bundlePath.asString(),
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
                CommonRestApi.paramBundlePath to objectLocation.bundlePath.asString(),
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
                CommonRestApi.paramBundlePath to objectLocation.bundlePath.asString(),
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
                CommonRestApi.paramBundlePath to objectLocation.bundlePath.asString(),
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
                CommonRestApi.commandRefactorRename,
                CommonRestApi.paramBundlePath to objectLocation.bundlePath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString(),
                CommonRestApi.paramObjectName to newName.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun executionModel(): ExecutionModelEncoding {
        val responseText = httpGet("$baseUrl${CommonRestApi.actionModel}")

        val responseJson = JSON.parse<Array<Json>>(responseText)
        val responseCollection = ClientJsonUtils.toList(responseJson)

        @Suppress("UNCHECKED_CAST")
        return ExecutionModelEncoding.fromCollection(
                responseCollection as List<Map<String, Any>>)
    }


    suspend fun startExecution(): Digest {
        val responseText = httpGet("$baseUrl${CommonRestApi.actionStart}")
        return Digest.parse(responseText)
    }


    suspend fun resetExecution(
//            objectName: String
    ): Digest {
        val responseText = httpGet("$baseUrl${CommonRestApi.actionReset}")
        return Digest.parse(responseText)
    }


    suspend fun performAction(
            objectLocation: ObjectLocation
    ): ExecutionResultResponse {
        val responseJson = getJson(
                CommonRestApi.actionPerform,
                CommonRestApi.paramBundlePath to objectLocation.bundlePath.asString(),
                CommonRestApi.paramObjectPath to objectLocation.objectPath.asString())

        @Suppress("UNCHECKED_CAST")
        val responseCollection = ClientJsonUtils.toMap(responseJson) as Map<String, String?>

//        val status = responseJson[CommonRestApi.fieldStatus] as String
//        val digest = responseJson[CommonRestApi.fieldDigest] as String

        return ExecutionResultResponse.fromCollection(responseCollection)
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