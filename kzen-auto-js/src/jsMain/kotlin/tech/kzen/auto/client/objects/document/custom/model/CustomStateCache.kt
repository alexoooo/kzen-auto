package tech.kzen.auto.client.objects.document.custom.model

import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.service.parse.NotationParser


data class CustomStateCache(
    val editorModified: Boolean
) {
    companion object {
        fun compute(
            editorValue: String,
            serverNotation: DocumentObjectNotation,
            notationParser: NotationParser
        ): CustomStateCache {
            val modified = try {
                notationParser.parseDocumentObjects(editorValue) != serverNotation
            }
            catch (e: Throwable) {
                true
            }
            return CustomStateCache(modified)
        }
    }
}
