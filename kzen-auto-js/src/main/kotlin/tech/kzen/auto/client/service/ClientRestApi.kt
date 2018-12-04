package tech.kzen.auto.client.service

import tech.kzen.auto.client.util.encodeURIComponent
import tech.kzen.auto.client.util.httpGet
import tech.kzen.auto.common.api.ActionExecution
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.exec.ExecutionStatus
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.IoUtils
import tech.kzen.lib.platform.client.ClientJsonUtils
import kotlin.js.Json


class ClientRestApi(
        private val baseUrl: String
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun scanNotationPaths(): Map<ProjectPath, Digest> {
        val scanText = httpGet("$baseUrl${CommonRestApi.scan}")

        val builder = mutableMapOf<ProjectPath, Digest>()
        // NB: using transform just to iterate the Json, is there a better way to do this?
        JSON.parse<Json>(scanText) { key: String, value: Any? ->
            if (value is String) {
                builder[ProjectPath(key)] = Digest.decode(value)
            }
            null
        }
        return builder
    }


    suspend fun readNotation(location: ProjectPath): ByteArray {
        @Suppress("UNUSED_VARIABLE")
        val response = httpGet("$baseUrl${CommonRestApi.notationPrefix}" +
                location.relativeLocation)

        return IoUtils.stringToUtf8(response)
    }



    //-----------------------------------------------------------------------------------------------------------------
    suspend fun editCommand(
            objectName: String,
            parameterPath: String,
            deparsedParameter: String
    ): Digest {
        val encodedName = encodeURIComponent(objectName)
        val encodedParameter = encodeURIComponent(parameterPath)
        val encodedValue = encodeURIComponent(deparsedParameter)

        val digest = httpGet("$baseUrl${CommonRestApi.commandEdit}" +
                "?name=$encodedName" +
                "&parameter=$encodedParameter" +
                "&value=$encodedValue")

        return Digest.decode(digest)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun addCommand(
            projectPath: ProjectPath,
            objectName: String,
            deparsedBody: String,
            index: Int
    ): Digest {
        val encodedPath = encodeURIComponent(projectPath.relativeLocation)
        val encodedName = encodeURIComponent(objectName)
        val encodedBody = encodeURIComponent(deparsedBody)

        val digest = httpGet("$baseUrl${CommonRestApi.commandAdd}" +
                "?path=$encodedPath" +
                "&name=$encodedName" +
                "&body=$encodedBody" +
                "&index=$index")
        return Digest.decode(digest)

    }


    suspend fun removeCommand(
            objectName: String
    ): Digest {
        val encodedName = encodeURIComponent(objectName)
        val digest = httpGet("$baseUrl${CommonRestApi.commandRemove}" +
                "?name=$encodedName")
        return Digest.decode(digest)
    }


    suspend fun shiftCommand(
            objectName: String,
            indexInPackage: Int
    ): Digest {
        val encodedName = encodeURIComponent(objectName)
        val encodedIndex = encodeURIComponent(indexInPackage.toString())

        val digest = httpGet("$baseUrl${CommonRestApi.commandShift}" +
                "?name=$encodedName&index=$encodedIndex")
        return Digest.decode(digest)
    }


    suspend fun renameCommand(
            objectName: String,
            newName: String
    ): Digest {
        val encodedName = encodeURIComponent(objectName)
        val encodedNewName = encodeURIComponent(newName)

        val digest = httpGet("$baseUrl${CommonRestApi.commandRename}" +
                "?name=$encodedName&to=$encodedNewName")
        return Digest.decode(digest)
    }



    //-----------------------------------------------------------------------------------------------------------------
    suspend fun createPackage(
            projectPath: ProjectPath
    ): Digest {
        val encodedPath = encodeURIComponent(projectPath.relativeLocation)

        val digest = httpGet("$baseUrl${CommonRestApi.commandCreate}" +
                "?path=$encodedPath")
        return Digest.decode(digest)
    }


    suspend fun deletePackage(
            projectPath: ProjectPath
    ): Digest {
        val encodedPath = encodeURIComponent(projectPath.relativeLocation)

        val digest = httpGet("$baseUrl${CommonRestApi.commandDelete}" +
                "?path=$encodedPath")
        return Digest.decode(digest)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun executionModel(): ExecutionModel {
        val responseText = httpGet("$baseUrl${CommonRestApi.actionModel}")

        val responseJson = JSON.parse<Array<Json>>(responseText)
        val responseCollection = ClientJsonUtils.toList(responseJson)

//        val frames = mutableListOf<ExecutionFrame>()
//        for (responseFrame in responseFrames) {
//
//            val nameToUrl = mutableMapOf<String, String>()
//            for (property in responseFrame.getOwnPropertyNames()) {
//                nameToUrl[property] = responseFrame[property] as String
//            }
//
//            frames.add(ExecutionFrame(
//                    ))
//        }

        @Suppress("UNCHECKED_CAST")
        return ExecutionModel.fromCollection(responseCollection as List<Map<String, Any>>)
    }


    suspend fun startExecution(): Digest {
        val responseText = httpGet("$baseUrl${CommonRestApi.actionStart}")
        return Digest.decode(responseText)
    }


    suspend fun resetExecution(
//            objectName: String
    ): Digest {
        val responseText = httpGet("$baseUrl${CommonRestApi.actionReset}")
        return Digest.decode(responseText)
    }


    suspend fun performAction(
            objectName: String
    ): ActionExecution {
        val encodedName = encodeURIComponent(objectName)

        val responseText = httpGet("$baseUrl${CommonRestApi.actionPerform}" +
                "?name=$encodedName")

        val responseJson = JSON.parse<Json>(responseText)

        val status = responseJson["status"] as String
        val digest = responseJson["digest"] as String

        return ActionExecution(
                ExecutionStatus.valueOf(status),
                Digest.decode(digest))
    }
}