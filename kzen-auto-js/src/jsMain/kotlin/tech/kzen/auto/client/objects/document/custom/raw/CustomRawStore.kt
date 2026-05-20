package tech.kzen.auto.client.objects.document.custom.raw

import tech.kzen.auto.client.objects.document.custom.model.CustomStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.model.structure.notation.cqrs.SetDocumentObjectsCommand
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphSuccess


class CustomRawStore(
    private val parent: CustomStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun onEditorChange(newValue: String) {
        parent.update { it.withRaw { raw -> raw.copy(editorValue = newValue) } }
    }


    fun onSave() {
        val snapshot = parent.stateOrNull()
            ?: return

        if (snapshot.raw.saving || !snapshot.editorModified) {
            return
        }

        val payload = snapshot.raw.editorValue
        val documentPath = snapshot.documentPath

        parent.update { it.withRaw { raw -> raw.copy(saving = true, lastError = null) } }

        async {
            val parsed = try {
                ClientContext.notationParser.parseDocumentObjects(payload)
            }
            catch (e: Throwable) {
                parent.update { it.withRaw { raw -> raw.copy(saving = false, lastError = e.message ?: e.toString()) } }
                return@async
            }

            val result = ClientContext.mirroredGraphStore.apply(
                SetDocumentObjectsCommand(documentPath, parsed))

            when (result) {
                is MirroredGraphSuccess ->
                    parent.update { it.withRaw { raw -> raw.copy(saving = false) } }

                is MirroredGraphError ->
                    parent.update {
                        it.withRaw { raw ->
                            raw.copy(saving = false, lastError = result.error.message ?: result.error.toString())
                        }
                    }
            }
        }
    }
}
