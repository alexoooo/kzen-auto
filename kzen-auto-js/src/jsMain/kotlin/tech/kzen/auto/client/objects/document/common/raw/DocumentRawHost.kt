package tech.kzen.auto.client.objects.document.common.raw

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.store.MirroredGraphStore


// The seam between a document store (CustomStore / ScriptStore) and the generic raw editor stack.
// The host owns the raw sub-state; DocumentRawStore reads a snapshot and writes back via updateRaw.
interface DocumentRawHost {
    val notationParser: NotationParser
    val mirroredGraphStore: MirroredGraphStore

    fun rawSnapshot(): DocumentRawSnapshot?

    fun updateRaw(updater: (DocumentRawState) -> DocumentRawState)
}


data class DocumentRawSnapshot(
    val documentPath: DocumentPath,
    val raw: DocumentRawState,
    val editorModified: Boolean
)
