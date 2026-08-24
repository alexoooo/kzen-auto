package tech.kzen.auto.server.objects.data.schema

import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldListSpec
import tech.kzen.lib.common.reflect.Reflect


/**
 * Authored structural schema. Field [tech.kzen.lib.common.model.structure.metadata.TypeMetadata] is preserved in
 * notation, while today's [DataShape] transport intentionally publishes ordered labels only.
 */
@Reflect
class DataSchemaDocument(
    val fields: DataSchemaFieldListSpec
):
    DocumentArchetype()
{
    fun shape(): DataShape.Tabular =
        DataShape.Tabular(HeaderListing.ofUnique(fields.fields.keys.toList()))
}
