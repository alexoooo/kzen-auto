package tech.kzen.auto.client.objects.document.common.raw

import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.service.parse.NotationParser


object DocumentRawModified {
    // The editor differs from the server when its YAML parses to a different object notation —
    // or fails to parse at all (in which case it can't equal the server, so treat as modified).
    fun compute(
        editorValue: String,
        serverNotation: DocumentObjectNotation,
        notationParser: NotationParser
    ): Boolean {
        return try {
            notationParser.parseDocumentObjects(editorValue) != serverNotation
        }
        catch (e: Throwable) {
            true
        }
    }
}
