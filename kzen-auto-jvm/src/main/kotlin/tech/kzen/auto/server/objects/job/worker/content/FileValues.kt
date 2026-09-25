package tech.kzen.auto.server.objects.job.worker.content

import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import java.time.Instant
import kotlin.reflect.typeOf


/**
 * A file as a value, whether a selected file (`File`) or a member of an archive (`Extract`): the payload is its
 * [Content] (an opaque native, read by `Parse`, `Extract` or `Write`), and the metadata is plain data describing
 * it — [name], [path], [size], [modified], [kind], then any values taken from its name (the Name pattern's
 * captures), then [parent], the metadata of the value it was found in (an archive member's archive). Nothing
 * here is a type of its own: the value is an ordinary payload with a metadata record.
 */
object FileValues {
    const val name = "name"
    const val path = "path"
    const val size = "size"
    const val modified = "modified"
    const val kind = "kind"
    const val parent = "parent"

    const val kindFile = "file"


    private val text = DataType.Scalar(ScalarKind.Text)
    private val size64 = DataType.Scalar(ScalarKind.Integer(64))
    private val nullableInstant = DataType.Scalar(ScalarKind.Instant, nullable = true)

    /** The payload's contract: a [Content], opaque. */
    val contentContract: DataContract by lazy { JobDataValues.describe(typeOf<Content>()) }


    /** The metadata's contract for a file whose name yields [captures], found in a value with [parent] metadata. */
    fun metadataContract(captures: List<String>, parent: MetadataContract?): MetadataContract {
        val fields = ArrayList<Pair<FieldId, DataContract>>()
        fields += FieldId(name) to DataContract(text)
        fields += FieldId(path) to DataContract(text)
        fields += FieldId(size) to DataContract(size64)
        fields += FieldId(modified) to DataContract(nullableInstant)
        fields += FieldId(kind) to DataContract(text)
        captures.forEach { fields += FieldId(it) to DataContract(text) }
        if (parent != null) {
            fields += FieldId(FileValues.parent) to parent.contract
        }
        return MetadataContract.of(DataOverlay.contract(null, fields))
    }


    /** The contract of a file value: [contentContract] with [metadataContract]. */
    fun contract(captures: List<String>, parent: MetadataContract?): DataContract =
        contentContract.withMetadata(metadataContract(captures, parent))


    /** The metadata record of one file. */
    fun metadata(
        name: String,
        path: String,
        size: Long,
        modifiedEpochMillis: Long?,
        kind: String,
        captures: Map<String, String>,
        parent: ValueMetadata?
    ): ValueMetadata {
        val fields = ArrayList<Pair<FieldId, DataValue>>()
        fields += FieldId(FileValues.name) to literal(name, text)
        fields += FieldId(FileValues.path) to literal(path, text)
        fields += FieldId(FileValues.size) to literal(size, size64)
        fields += FieldId(modified) to literal(
            modifiedEpochMillis?.let { Instant.ofEpochMilli(it).toString() }, nullableInstant)
        fields += FieldId(FileValues.kind) to literal(kind, text)
        captures.forEach { (key, value) -> fields += FieldId(key) to literal(value, text) }
        if (parent != null) {
            fields += FieldId(FileValues.parent) to parent.value
        }
        return ValueMetadata.of(DataOverlay.record(null, fields))
    }


    /** A file value: [content] as the payload, [metadata] describing it. */
    fun lift(content: Content, metadata: ValueMetadata): DataValue =
        JobDataValues.lift(content, contentContract).withMetadata(metadata)


    private fun literal(value: Any?, type: DataType.Scalar): DataValue =
        LiteralDataValues.lift(value, DataContract(type))
}
