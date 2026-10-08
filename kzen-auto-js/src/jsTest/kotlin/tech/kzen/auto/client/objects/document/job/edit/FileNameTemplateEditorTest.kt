package tech.kzen.auto.client.objects.document.job.edit

import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.type.ScalarKind
import kotlin.test.Test
import kotlin.test.assertEquals


class FileNameTemplateEditorTest {
    private val text = DataType.Scalar(ScalarKind.Text)

    private val extension = FileNameTemplateEditor.Placeholder("extension", "This Worker", "the format's extension")


    private fun record(vararg fields: Pair<String, DataType>): DataType.Record =
        DataType.Record(fields.map { (name, type) -> DataField(FieldId(name), type) })


    // A row read from a file: its columns, and its file under `parent`
    private val row = DataContract(
        record("station" to text, "max temp" to text),
        metadata = MetadataContract(record("parent" to record("name" to text))))


    @Test
    fun columnsComeFirstWhenTheWorkerReadsThem() {
        val placeholders = FileNameTemplateEditor.placeholders(row, true, listOf(extension))

        assertEquals(listOf("station", "parent.name", "extension"), placeholders.map { it.path })
        assertEquals(listOf("Columns", "From parent", "This Worker"), placeholders.map { it.group })
    }


    @Test
    fun withoutColumnsOnlyTheMetadataAndTheWorkersOwnAreOffered() {
        val placeholders = FileNameTemplateEditor.placeholders(row, false, listOf(extension))

        assertEquals(listOf("parent.name", "extension"), placeholders.map { it.path })
    }


    // As over Format's chunks: the output's name, then the record's metadata a level further up
    @Test
    fun metadataGroupsByLevel() {
        val chunk = DataContract(
            DataType.Scalar(ScalarKind.Binary),
            metadata = MetadataContract(record(
                "name" to text,
                "parent" to record("parent" to record("name" to text)))))

        val placeholders = FileNameTemplateEditor.placeholders(chunk, false, listOf())

        assertEquals(listOf("name", "parent.parent.name"), placeholders.map { it.path })
        assertEquals(listOf("This value", "From parent → parent"), placeholders.map { it.group })
    }
}
