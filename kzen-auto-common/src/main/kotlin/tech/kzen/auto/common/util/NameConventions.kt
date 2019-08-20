package tech.kzen.auto.common.util

import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.platform.DateTimeUtils


object NameConventions {
    fun randomAnonymous(): ObjectName {
        val prefix = AutoConventions.anonymousPrefix
        val timestampSuffix = DateTimeUtils.filenameTimestamp()
        return ObjectName("$prefix$timestampSuffix")
    }
}