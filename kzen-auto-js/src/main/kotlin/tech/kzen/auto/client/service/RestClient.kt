package tech.kzen.auto.client.service

import tech.kzen.auto.client.util.encodeURIComponent
import tech.kzen.auto.client.util.httpGet
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.util.Digest
import kotlin.js.Json


class RestClient(
        private val baseUrl: String
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun scanQuery(): Map<ProjectPath, Digest> {
        val scanText = httpGet("$baseUrl/scan")

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


    suspend fun readQuery(location: ProjectPath): ByteArray {
        @Suppress("UNUSED_VARIABLE")
        val response = httpGet("$baseUrl/notation/${location.relativeLocation}")

        // from kotlinx.serialization String.toUtf8Bytes
        val blck = js("unescape(encodeURIComponent(response))")
        return (blck as String).toList().map { it.toByte() }.toByteArray()
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

        val digest = httpGet(
                "$baseUrl/command/edit?name=$encodedName&parameter=$encodedParameter&value=$encodedValue")
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

        val digest = httpGet("$baseUrl/command/add" +
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
        val digest = httpGet("$baseUrl/command/remove?name=$encodedName")
        return Digest.decode(digest)
    }


    suspend fun shiftCommand(
            objectName: String,
            indexInPackage: Int
    ): Digest {
        val encodedName = encodeURIComponent(objectName)
        val encodedIndex = encodeURIComponent(indexInPackage.toString())

        val digest = httpGet("$baseUrl/command/shift?name=$encodedName&index=$encodedIndex")
        return Digest.decode(digest)
    }


    suspend fun renameCommand(
            objectName: String,
            newName: String
    ): Digest {
        val encodedName = encodeURIComponent(objectName)
        val encodedNewName = encodeURIComponent(newName)

        val digest = httpGet("$baseUrl/command/rename?name=$encodedName&to=$encodedNewName")
        return Digest.decode(digest)
    }



    //-----------------------------------------------------------------------------------------------------------------
    suspend fun createPackage(
            projectPath: ProjectPath
    ): Digest {
        val encodedPath = encodeURIComponent(projectPath.relativeLocation)

        val digest = httpGet("$baseUrl/command/create?path=$encodedPath")
        return Digest.decode(digest)
    }


    suspend fun deletePackage(
            projectPath: ProjectPath
    ): Digest {
        val encodedPath = encodeURIComponent(projectPath.relativeLocation)

        val digest = httpGet("$baseUrl/command/delete?path=$encodedPath")
        return Digest.decode(digest)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun performAction(
            objectName: String
    ) {
        val encodedName = encodeURIComponent(objectName)

        httpGet("$baseUrl/action/perform?name=$encodedName")
    }
}