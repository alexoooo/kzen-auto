package tech.kzen.auto.server.objects.pipeline.model

import tech.kzen.auto.common.util.data.DataLocationGroup
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException


class GroupPattern(
    val empty: Boolean,
    private val regex: Pattern?
) {
    companion object {
        val empty = GroupPattern(true, null)

        fun parse(groupBy: String): GroupPattern? {
            if (groupBy.isBlank()) {
                return empty
            }

            val regex =
                try {
                    Pattern.compile(groupBy)
                }
                catch (e: PatternSyntaxException) {
                    return null
                }

            return GroupPattern(false, regex)
        }
    }


    fun extract(fileName: String): DataLocationGroup {
        if (empty) {
            return DataLocationGroup.empty
        }

        val matcher = regex!!.matcher(fileName)
        if (! matcher.find()) {
            return DataLocationGroup.other
        }

        val groupText =
            if (matcher.groupCount() > 0) {
                matcher.group(1)
            }
            else {
                matcher.group()
            }

        if (groupText.isNullOrEmpty()) {
            return DataLocationGroup.empty
        }

        return DataLocationGroup(groupText)
    }
}