package tech.kzen.auto.client.objects.document.common.edit

import tech.kzen.lib.common.model.attribute.AttributePath


object CommonEditUtils {
    fun formattedLabel(
        attributePath: AttributePath,
        labelOverride: String? = null
    ): String {
        if (labelOverride != null) {
            return labelOverride
        }

        val defaultLabel =
            if (attributePath.nesting.segments.isEmpty()) {
                attributePath.attribute.value
            }
            else {
                attributePath.nesting.segments.last().asString()
            }

        val upperCamelCase = defaultLabel
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        val results = Regex("\\w+").findAll(upperCamelCase)
        val words = results.map { it.groups[0]!!.value }

        return words.joinToString(" ")
    }
}