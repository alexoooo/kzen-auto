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
        val inputAttribute = AttributeName("input")
        val outputAttribute = AttributeName("output")
    }
}