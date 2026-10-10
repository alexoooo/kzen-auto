package tech.kzen.auto.server.objects.job.worker.format

import tech.kzen.auto.server.objects.job.worker.content.FileValues
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.exec.data.value.overlay.MetadataShape


/**
 * A chunk's metadata record `{group, format, parent}`: the group whose output it belongs to (`""` when records
 * are not grouped), the format that encoded it (`{extension}`, without the dot), and, when known, the metadata of
 * the value it encodes. A [shape] serves every chunk whose parent metadata has its contract, so a chunk whose parent
 * metadata is a new object (each record after a Formula) costs its record, not a contract.
 */
internal object ChunkMetadata {
    const val group = "group"
    const val format = "format"
    const val extension = "extension"

    private val text = DataContract(DataType.Scalar(ScalarKind.Text))
    private val groupField = FieldId(group)
    private val formatField = FieldId(format)
    private val extensionField = FieldId(extension)
    private val parentField = FieldId(FileValues.parent)
    private val formatContract = DataOverlay.contract(null, listOf(extensionField to text))


    /** The shape of chunk metadata over parent metadata of contract [parent]: `group`, `format`, then `parent`. */
    fun shape(parent: DataContract?): MetadataShape {
        val fields = ArrayList<Pair<FieldId, DataContract>>(3)
        fields += groupField to text
        fields += formatField to formatContract
        if (parent != null) {
            fields += parentField to parent
        }
        return MetadataShape(null, fields)
    }


    /** The group field's value, made once per group. */
    fun group(group: String): DataValue =
        LiteralDataValues.lift(group, text)


    /** The format field's value, made once per format: its first [extension] without the dot, or empty. */
    fun format(extension: String): DataValue =
        DataOverlay.record(null, listOf(extensionField to LiteralDataValues.lift(extension, text)))


    /** The field values of a chunk of [group] in [format] over [parent], for its [shape]. */
    fun values(group: DataValue, format: DataValue, parent: ValueMetadata?): List<DataValue> =
        if (parent == null) listOf(group, format) else listOf(group, format, parent.value)
}
