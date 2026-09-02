package tech.kzen.auto.server.objects.data.schema

import tech.kzen.auto.common.data.schema.AuthoredRecordSchema
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldListSpec
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldSpec
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.lib.common.exec.data.shape.ShapeStability
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals


class DataSchemaDocumentTest {
    @Test
    fun delegatesContractAndServesDeclaredShape() {
        val fields = DataSchemaFieldListSpec(mapOf(
            "name" to DataSchemaFieldSpec(TypeMetadata.string)))
        val authored = AuthoredRecordSchema(fields)
        val document = DataSchemaDocument(fields)

        assertEquals(authored.contract(), document.contract())
        assertEquals(authored.contract(), document.shape().itemType)
        assertEquals(ShapeProvenance.Declared, document.shape().provenance)
        assertEquals(ShapeStability.Stable, document.shape().stability)
    }
}
