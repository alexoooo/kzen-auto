package tech.kzen.auto.server.objects.report.group

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
        if (! matcher.matches()) {
            return DataLocationGroup.other
        }

        val groupText = matcher.group()
        if (groupText.isNullOrEmpty()) {
            return DataLocationGroup.empty
        }

        return DataLocationGroup(groupText)
    }
}