package tech.kzen.auto.common.data.file

import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.platform.collect.toPersistentList


data class FileSelectionSpec(
    val entries: List<FileSelectionEntry>
): Digestible {
    companion object {
        fun ofNotation(notation: ListAttributeNotation): FileSelectionSpec {
            return FileSelectionSpec(notation.values.mapIndexed { index, entry ->
                FileSelectionEntry.ofNotation(entry as? MapAttributeNotation
                    ?: throw IllegalArgumentException("File selection entry $index must be a map: $entry"))
            })
        }
    }


    fun asNotation(): ListAttributeNotation {
        return ListAttributeNotation(entries.map(FileSelectionEntry::asNotation).toPersistentList())
    }


    override fun digest(sink: Digest.Sink) {
        sink.addDigestibleList(entries)
    }
}
