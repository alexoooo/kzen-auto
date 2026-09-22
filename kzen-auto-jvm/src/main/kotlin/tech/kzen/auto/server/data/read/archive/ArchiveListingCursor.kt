package tech.kzen.auto.server.data.read.archive

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.plugin.api.data.ReaderByteInput
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.RecordLiteral
import java.util.concurrent.CancellationException


/**
 * One record per member header of a tar archive, in archive order; member bodies are skipped, never buffered.
 * The bytes arrive with any content coding (gzip) already removed.
 */
class ArchiveListingCursor(
    bytes: ReaderByteInput,
    override val shape: DataShape
): DataCursor {
    companion object {
        const val nameField = "name"
        const val sizeField = "size"
        const val modifiedField = "modified"
        const val kindField = "kind"

        const val kindFile = "file"
        const val kindDirectory = "directory"
        const val kindLink = "link"
        const val kindOther = "other"

        val contract = DataContract(DataType.Record(listOf(
            DataField(FieldId(nameField, 0), DataType.Scalar(ScalarKind.Text)),
            DataField(FieldId(sizeField, 0), DataType.Scalar(ScalarKind.Integer(64, true))),
            DataField(FieldId(modifiedField, 0), DataType.Scalar(ScalarKind.Instant, true)),
            DataField(FieldId(kindField, 0), DataType.Scalar(ScalarKind.Text)))))
    }


    private val archive = TarArchiveInputStream(ReaderByteInputStream(bytes))
    private var buffered: TarArchiveEntry? = null
    private var exhausted = false
    private var closed = false


    override fun hasNext(): Boolean {
        check(!closed) { "Archive listing cursor is closed" }
        if (buffered != null) return true
        if (exhausted) return false
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException("Archive listing interrupted")
        }
        buffered = archive.nextEntry
        exhausted = buffered == null
        return !exhausted
    }


    override fun next(): DataValue {
        if (!hasNext()) throw NoSuchElementException()
        val header = requireNotNull(buffered).also { buffered = null }
        return LiteralDataValues.lift(
            RecordLiteral.of(mapOf(
                nameField to header.name,
                sizeField to header.size,
                modifiedField to header.lastModifiedTime?.toInstant()?.toString(),
                kindField to kind(header))),
            contract)
    }


    private fun kind(header: TarArchiveEntry): String =
        when {
            header.isDirectory -> kindDirectory
            header.isSymbolicLink || header.isLink -> kindLink
            header.isFile -> kindFile
            else -> kindOther
        }


    override fun close() {
        if (closed) return
        closed = true
        buffered = null
        archive.close()
    }
}
