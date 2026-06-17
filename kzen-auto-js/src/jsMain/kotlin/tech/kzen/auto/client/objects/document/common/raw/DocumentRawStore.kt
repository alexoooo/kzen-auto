package tech.kzen.auto.client.objects.document.common.raw

import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.model.structure.notation.cqrs.SetDocumentObjectsCommand
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphSuccess


class DocumentRawStore(
    private val host: DocumentRawHost
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun onEditorChange(newValue: String) {
        host.updateRaw { raw -> raw.copy(editorValue = newValue) }
    }


    fun onSave() {
        val snapshot = host.rawSnapshot()
            ?: return

        if (snapshot.raw.saving || !snapshot.editorModified) {
            return
        }

        val payload = snapshot.raw.editorValue
        val documentPath = snapshot.documentPath

        host.updateRaw { raw -> raw.copy(saving = true, lastError = null) }

        async {
            val parsed = try {
                host.notationParser.parseDocumentObjects(payload)
            }
            catch (e: Throwable) {
                host.updateRaw { raw -> raw.copy(saving = false, lastError = e.message ?: e.toString()) }
                return@async
            }

            val result = host.mirroredGraphStore.apply(
                SetDocumentObjectsCommand(documentPath, parsed))

            when (result) {
                is MirroredGraphSuccess ->
                    host.updateRaw { raw -> raw.copy(saving = false) }

                is MirroredGraphError ->
                    host.updateRaw { raw ->
                        raw.copy(saving = false, lastError = result.error.message ?: result.error.toString())
                    }
            }
        }
    }
}
