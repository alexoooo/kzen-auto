package tech.kzen.auto.client.objects.action

import tech.kzen.lib.common.api.model.ObjectName
import kotlin.js.Date


object NameConventions {
    private const val defaultPrefix = "__ANON__"


    fun isDefault(objectName: ObjectName): Boolean {
        return objectName.value.startsWith(defaultPrefix)
    }

    fun randomAnonymous(): ObjectName {
        val timestampSuffix = Date().toISOString()
        return ObjectName("$defaultPrefix$timestampSuffix")
    }
}