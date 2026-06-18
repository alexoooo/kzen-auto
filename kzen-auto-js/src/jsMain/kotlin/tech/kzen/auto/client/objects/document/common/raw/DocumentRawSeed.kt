package tech.kzen.auto.client.objects.document.common.raw

import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.service.parse.NotationParser


// Seed the raw YAML editor with a trailing newline so the final content line is followed by an
// empty line — easier to select and paste over. The newline is purely cosmetic editor text: both
// modified-detection (DocumentRawModified) and save re-parse the YAML, which ignores trailing
// whitespace, so the seeded value still reads as "not modified" against the server notation.
fun NotationParser.unparseDocumentForRawEditor(notation: DocumentObjectNotation): String {
    return unparseDocument(notation, "") + "\n"
}
