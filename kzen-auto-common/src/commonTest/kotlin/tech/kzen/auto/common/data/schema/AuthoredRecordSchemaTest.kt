package tech.kzen.auto.common.data.schema

import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldListSpec
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldSpec
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


class AuthoredRecordSchemaTest {
    @Test
    fun buildsOrderedRecordContract() {
        val schema = AuthoredRecordSchema(DataSchemaFieldListSpec(linkedMapOf(
            "name" to DataSchemaFieldSpec(TypeMetadata.string),
            "amount" to DataSchemaFieldSpec(TypeMetadata.int))))

        assertEquals(
            listOf("name", "amount"),
            assertIs<DataType.Record>(schema.contract().structural).fields.map { it.id.name })
    }
}
