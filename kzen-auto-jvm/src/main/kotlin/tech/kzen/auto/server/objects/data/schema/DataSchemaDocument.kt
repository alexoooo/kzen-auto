package tech.kzen.auto.server.objects.data.schema

import tech.kzen.auto.common.data.schema.AuthoredRecordSchema
import tech.kzen.auto.common.data.schema.RecordSchema
import tech.kzen.auto.common.data.schema.declaredShape
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldListSpec
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.reflect.Reflect


/**
 * Dedicated document wrapper over an authored record schema.
 */
@Reflect
class DataSchemaDocument(
    val fields: DataSchemaFieldListSpec
):
    DocumentArchetype(),
    RecordSchema
{
    private val schema = AuthoredRecordSchema(fields)


    override fun contract(): DataContract = schema.contract()


    fun shape() = schema.declaredShape()
}
