package tech.kzen.auto.client.objects.document.job.edit

import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.type.ScalarKind
import kotlin.test.Test
import kotlin.test.assertEquals


class KotlinFieldEditorTest {
    private val text = DataType.Scalar(ScalarKind.Text)

    private val extension = KotlinFieldEditor.Placeholder("extension", "This Worker", "the file's extension")


    private fun record(vararg fields: Pair<String, DataType>): DataType.Record =
        DataType.Record(fields.map { (name, type) -> DataField(FieldId(name), type) })


    // A row read from a file: its columns, and its file under `parent`
    private val row = DataContract(
        record("station" to text, "max temp" to text),
        metadata = MetadataContract(record("parent" to record("name" to text))))


    @Test
    fun columnsComeFirstThenTheMetadataThenTheWorkersOwn() {
        val placeholders = KotlinFieldEditor.placeholders(row, listOf(extension))

        assertEquals(listOf("station", "`max temp`", "parent.name", "extension"), placeholders.map { it.reference })
        assertEquals(listOf("Columns", "Columns", "From parent", "This Worker"), placeholders.map { it.group })
    }


    // As over Format's chunks: no columns, the output's group, then the record's metadata a level further up
    @Test
    fun metadataGroupsByLevel() {
        val chunk = DataContract(
            DataType.Scalar(ScalarKind.Binary),
            metadata = MetadataContract(record(
                "group" to text,
                "parent" to record("parent" to record("name" to text)))))

        val placeholders = KotlinFieldEditor.placeholders(chunk, listOf())

        assertEquals(listOf("group", "parent.parent.name"), placeholders.map { it.reference })
        assertEquals(listOf("This value", "From parent → parent"), placeholders.map { it.group })
    }


    // A bare name reaches the column first, so the metadata field of that name is reached through `meta.`; a
    // metadata field named `meta` has no name in an expression
    @Test
    fun aMetadataFieldNamedAsAColumnIsReachedThroughMeta() {
        val contract = DataContract(
            record("name" to text),
            metadata = MetadataContract(record("name" to text, "meta" to text)))

        val placeholders = KotlinFieldEditor.placeholders(contract, listOf())

        assertEquals(listOf("name", "meta.name"), placeholders.map { it.reference })
    }


    @Test
    fun aTemplateInsertsAReferenceInsideBraces() {
        assertEquals("\${parent.name}", KotlinFieldEditor.inserted("parent.name", template = true))
        assertEquals("parent.name", KotlinFieldEditor.inserted("parent.name", template = false))
    }
}
