package tech.kzen.auto.server.objects.data

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.data.spec.FieldFormatListSpec
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DataFormatDocument(
    val fields: FieldFormatListSpec
):
    DocumentArchetype()
{
}