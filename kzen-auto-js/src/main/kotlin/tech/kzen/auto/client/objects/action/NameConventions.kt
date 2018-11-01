package tech.kzen.auto.client.objects.action

import kotlin.js.Date


object NameConventions {
    private const val defaultPrefix = "__DEFAULT__"


    fun isDefault(objectName: String): Boolean {
        return objectName.startsWith(defaultPrefix)
    }

    fun randomDefault(): String {
        val timestampSuffix = Date().toISOString()
        return "$defaultPrefix$timestampSuffix"
    }
}