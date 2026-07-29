package tech.kzen.auto.test

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.kzen.auto.common.objects.document.logic.context.LogicContextAnalysis
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.collect.toPersistentMap
import tech.kzen.lib.server.notation.ClasspathNotationMedia


/**
 * The self-test suite's own notation, checked against the context analysis (logic-spec §6): every document
 * under `main/` must declare what it owns and what it needs, so opening the suite in the editor is clean.
 *
 * This is a plain unit test, NOT part of the opt-in browser-driven `selfTest` suite — it reads notation off
 * the classpath and runs the analysis directly, so it costs nothing and runs on every `build`. That matters:
 * the declarations are data the definition layer never gates on (weak references), so nothing else in the
 * build would notice them drifting out of date as the suite's documents evolve.
 */
class SelfTestContextDeclarationsTest {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // The one warning the suite is SUPPOSED to carry. `main/Script.yaml` is a bare harness that runs the
        // shared `Insert Last` library script and provides no browser, so it genuinely cannot run standalone
        // — the warning states a true fact about it. Silencing it would mean weakening the analysis or lying
        // in the notation; leaving it is the documented resolution.
        private val expectedWarningDocuments = setOf("main/Script.yaml")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun selfTestDocumentsCarryTheirContextDeclarations() {
        val graphNotation = readClasspathNotation()

        val documentsWithWarnings = graphNotation
            .documents
            .map
            .keys
            .filter { it.asString().startsWith("main/") }
            .filter { LogicContextAnalysis.analyze(graphNotation, it).isNotEmpty() }
            .map { it.asString() }
            .toSortedSet()

        assertEquals(
            expectedWarningDocuments.toSortedSet(),
            documentsWithWarnings,
            "self-test documents with unexpected context warnings — each either consumes a Context without " +
                    "declaring `context.requires`, or hosts a document whose provide escapes into no slot")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Classpath only, and deliberately WITHOUT the `main/` exclusion kzen-auto-jvm's own test bootstrap
    // applies: those documents are exactly what this test is about. The suite's notation plus every
    // archetype it inherits from (kzen-auto-jvm's `auto-common/` and `auto-jvm/`) are all on this module's
    // runtime classpath.
    private fun readClasspathNotation(): GraphNotation {
        val media = ClasspathNotationMedia()
        val parser = YamlNotationParser()

        return runBlocking {
            val builder = mutableMapOf<DocumentPath, DocumentNotation>()

            for (documentPath in media.scan().documents.map.keys) {
                if (documentPath.folder) {
                    continue
                }
                val body = media.readDocument(documentPath)
                builder[documentPath] = DocumentNotation(parser.parseDocumentObjects(body), null)
            }

            GraphNotation(DocumentPathMap(builder.toPersistentMap()))
        }
    }
}
