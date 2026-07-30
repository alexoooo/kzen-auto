package tech.kzen.auto.test

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.kzen.auto.common.objects.document.logic.context.LogicContextAnalysis
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.collect.toPersistentMap
import tech.kzen.lib.server.notation.ClasspathNotationMedia


/**
 * The self-test suite's own notation, checked against the context analysis (logic-spec §6): every document
 * under `main/` must declare what it exports and what it needs, so the suite carries no finding at all —
 * opening it in the editor is clean and every document's Run stays enabled.
 *
 * This is a plain unit test, NOT part of the opt-in browser-driven `selfTest` suite — it reads notation off
 * the classpath and runs the analysis directly, so it costs nothing and runs on every `build`. That matters:
 * the declarations are data the definition layer never gates on (weak references), so nothing else in the
 * build would notice them drifting out of date as the suite's documents evolve.
 */
class SelfTestContextDeclarationsTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun selfTestDocumentsCarryTheirContextDeclarations() {
        val graphNotation = readClasspathNotation()

        val findings = graphNotation
            .documents
            .map
            .keys
            .filter { it.asString().startsWith("main/") }
            .sortedBy { it.asString() }
            .flatMap { findingLines(graphNotation, it) }

        assertEquals(
            listOf<String>(),
            findings,
            "context findings in the self-test suite's own notation — an ERROR means that document's Run is " +
                    "disabled (a step or a hosted document requires a Context nothing supplies), a WARNING " +
                    "is advisory (a declaration nothing backs, a dangling name, an aliased resource key)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // One line per finding, severity first: a failure names the document, the object and the reason, and an
    // error is distinguishable at a glance from an advisory warning.
    private fun findingLines(graphNotation: GraphNotation, documentPath: DocumentPath): List<String> {
        val findings = LogicContextAnalysis.analyze(graphNotation, documentPath)

        fun lines(severity: String, messages: Map<ObjectPath, String>): List<String> {
            return messages
                .entries
                .sortedBy { it.key.asString() }
                .map { "$severity ${documentPath.asString()}#${it.key.asString()}: ${it.value}" }
        }

        return lines("ERROR", findings.errors) + lines("WARNING", findings.warnings)
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
