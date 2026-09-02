package tech.kzen.auto.common.data.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName


class AuthoredRecordSchemaDraftTest {
    @Test
    fun preservesFieldOrderAndScalarCandidates() {
        val contract = DataContract(DataType.Record(listOf(
            DataField(FieldId("key"), DataType.Scalar(ScalarKind.Text)),
            DataField(FieldId("value"), DataType.Scalar(ScalarKind.Decimal, nullable = true), optional = true))))

        val draft = AuthoredRecordSchemaDraft.from(contract)!!

        assertEquals(listOf("key", "value"), draft.fields.keys.toList())
        assertEquals(TypeMetadata.string, draft.fields.getValue("key").typeMetadata)
        assertEquals(
            TypeMetadata(ClassName("java.math.BigDecimal"), emptyList(), nullable = true),
            draft.fields.getValue("value").typeMetadata)
    }


    @Test
    fun rejectsShapesThatCannotBeDeclaredByTheFlatSchemaEditor() {
        assertNull(AuthoredRecordSchemaDraft.from(DataContract(DataType.Dynamic())))
        assertNull(AuthoredRecordSchemaDraft.from(DataContract(DataType.Record(listOf(
            DataField(FieldId("nested"), DataType.Record(emptyList())))))))
        assertNull(AuthoredRecordSchemaDraft.from(DataContract(DataType.Record(listOf(
            DataField(FieldId("duplicate", 0), DataType.Scalar(ScalarKind.Text)),
            DataField(FieldId("duplicate", 1), DataType.Scalar(ScalarKind.Text)))))))
    }
}
