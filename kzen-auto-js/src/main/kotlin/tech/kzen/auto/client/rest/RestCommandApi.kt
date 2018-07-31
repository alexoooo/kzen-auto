package tech.kzen.auto.client.rest

import tech.kzen.auto.client.util.encodeURIComponent
import tech.kzen.auto.client.util.httpGet
import tech.kzen.lib.common.notation.model.ProjectPath


class RestCommandApi {
    suspend fun edit(
            objectName: String,
            parameterPath: String,
            valueYaml: String
    ) {
        val encodedName = encodeURIComponent(objectName)
        val encodedParameter = encodeURIComponent(parameterPath)
        val encodedValue = encodeURIComponent(valueYaml)

        httpGet("/command/edit?name=$encodedName&parameter=$encodedParameter&value=$encodedValue")
    }


    suspend fun add(
            objectName: String,
            typeName: String,
            projectPath: ProjectPath
    ) {
        val encodedName = encodeURIComponent(objectName)
        val encodedType = encodeURIComponent(typeName)
        val encodedPath = encodeURIComponent(projectPath.relativeLocation)

        httpGet("/command/add?name=$encodedName&type=$encodedType&path=$encodedPath")
    }


    suspend fun remove(
            objectName: String
    ) {
        val encodedName = encodeURIComponent(objectName)
        httpGet("/command/remove?name=$encodedName")
    }


    suspend fun shift(
            objectName: String,
            indexInPackage: Int
    ) {
        println("!!! Shifting: $objectName to $indexInPackage")

        val encodedName = encodeURIComponent(objectName)
        val encodedIndex = encodeURIComponent(indexInPackage.toString())

        httpGet("/command/shift?name=$encodedName&index=$encodedIndex")
    }


    suspend fun rename(
            objectName: String,
            newName: String
    ) {
        val encodedName = encodeURIComponent(objectName)
        val encodedNewName = encodeURIComponent(newName)

        httpGet("/command/rename?name=$encodedName&to=$encodedNewName")
    }
}