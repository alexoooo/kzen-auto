package tech.kzen.auto.server.objects.job.worker.format

import tech.kzen.auto.server.objects.job.worker.content.FileValues
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.exec.data.value.overlay.MetadataShape


/**
 * A chunk's metadata record `{name, parent}`: the output it belongs to and, when known, the metadata of the value it
 * encodes. A [shape] serves every chunk whose parent metadata has its contract, so a chunk whose parent metadata is a
 * new object (each record after a Formula) costs its record, not a contract.
 */
internal object ChunkMetadata {
    private val text = DataContract(DataType.Scalar(ScalarKind.Text))
    private val nameField = FieldId(FileValues.name)
    private val parentField = FieldId(FileValues.parent)


    /** The shape of chunk metadata over parent metadata of contract [parent]: `name`, then `parent` when known. */
    fun shape(parent: DataContract?): MetadataShape {
        val fields = ArrayList<Pair<FieldId, DataContract>>(2)
        fields += nameField to text
        if (parent != null) {
            fields += parentField to parent
        }
        return MetadataShape(null, fields)
    }


    /** The name field's value, made once per name. */
    fun name(name: String): DataValue =
        LiteralDataValues.lift(name, text)


    /** The field values of a chunk named [name] (from [ChunkMetadata.name]) over [parent], for its [shape]. */
    fun values(name: DataValue, parent: ValueMetadata?): List<DataValue> =
        if (parent == null) listOf(name) else listOf(name, parent.value)
}
