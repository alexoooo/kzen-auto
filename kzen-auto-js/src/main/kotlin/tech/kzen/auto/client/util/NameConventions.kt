package tech.kzen.auto.client.util

import tech.kzen.lib.common.api.model.ObjectName
import kotlin.js.Date


object NameConventions {
    private const val defaultPrefix = "__ANON__"


    fun isDefault(objectName: ObjectName): Boolean {
        return objectName.value.startsWith(defaultPrefix)
    }


    fun randomAnonymous(): ObjectName {
        // TODO: use local time zone

        val timestampSuffix = Date()
                .toISOString()
                .replace("-", "")
                .replace(":", "")
                .replace(".", "_")
        return ObjectName("$defaultPrefix$timestampSuffix")
    }
}