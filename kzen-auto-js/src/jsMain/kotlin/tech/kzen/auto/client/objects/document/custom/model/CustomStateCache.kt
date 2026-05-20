package tech.kzen.auto.client.objects.document.custom.model

import tech.kzen.auto.client.service.ClientContext
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation


data class CustomStateCache(
    val editorModified: Boolean
) {
    companion object {
        fun compute(editorValue: String, serverNotation: DocumentObjectNotation): CustomStateCache {
            val modified = try {
                ClientContext.notationParser.parseDocumentObjects(editorValue) != serverNotation
            }
            catch (e: Throwable) {
                true
            }
            return CustomStateCache(modified)
        }
    }
}
