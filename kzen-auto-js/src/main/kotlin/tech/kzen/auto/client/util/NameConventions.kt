package tech.kzen.auto.client.util

import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.obj.ObjectName
import kotlin.js.Date


object NameConventions {
    fun randomAnonymous(): ObjectName {
        val prefix = AutoConventions.anonymousPrefix

        // TODO: use local time zone
        val timestampSuffix = Date()
                .toISOString()
                .replace("-", "")
                .replace(":", "")
                .replace(".", "_")
        return ObjectName("$prefix$timestampSuffix")
    }
}