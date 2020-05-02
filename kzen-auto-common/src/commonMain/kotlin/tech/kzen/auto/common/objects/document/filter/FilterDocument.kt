package tech.kzen.auto.common.objects.document.filter

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class FilterDocument(
        val input: String,
        val output: String
):
        DocumentArchetype()
{
    companion object {
        const val indexKey = "index"
        const val inputKey = "input"
        const val outputKey = "output"

        val inputAttribute = AttributeName(inputKey)
        val outputAttribute = AttributeName(outputKey)
    }
}