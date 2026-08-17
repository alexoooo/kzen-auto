package tech.kzen.auto.common.util

import tech.kzen.auto.common.objects.document.flow.FlowConventions
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.logic.context.ContextConventions
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.parse.YamlNotationParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/**
 * [AutoConventions.isMainArchetype] is the one mechanism every document type identifies itself by;
 * [eachDocumentTypeRecognizesOnlyItsOwnArchetype] is what keeps the wrappers over it honest — one shared
 * mechanism must not make five distinct questions answer alike.
 */
class AutoConventionsTest {
    //-----------------------------------------------------------------------------------------------------------------
    private fun documentOf(yaml: String): DocumentNotation {
        return DocumentNotation(YamlNotationParser().parseDocumentObjects(yaml), null)
    }


    private fun documentDeclaring(archetype: String): DocumentNotation {
        return documentOf("main:\n  is: $archetype\n")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun mainDeclaringTheArchetypeMatches() {
        assertTrue(AutoConventions.isMainArchetype(documentDeclaring("Script"), ObjectName("Script")))
    }


    @Test
    fun anotherArchetypeDoesNotMatch() {
        assertFalse(AutoConventions.isMainArchetype(documentDeclaring("Flow"), ObjectName("Script")))
    }


    @Test
    fun aDocumentWithoutMainDoesNotMatch() {
        val document = documentOf("Other:\n  is: Script\n")
        assertFalse(AutoConventions.isMainArchetype(document, ObjectName("Script")))
    }


    @Test
    fun mainWithoutAnIsDoesNotMatch() {
        val document = documentOf("main:\n  title: untyped\n")
        assertFalse(AutoConventions.isMainArchetype(document, ObjectName("Script")))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun eachDocumentTypeRecognizesOnlyItsOwnArchetype() {
        val predicates = mapOf<String, (DocumentNotation) -> Boolean>(
            "Script" to ScriptConventions::isScript,
            "Flow" to FlowConventions::isFlow,
            "Job" to JobConventions::isJob,
            "Report" to ReportConventions::isReport,
            "Contexts" to ContextConventions::isContextsDocument)

        for ((archetype, predicate) in predicates) {
            val matched = predicates.keys.filter { predicate(documentDeclaring(it)) }
            assertEquals(listOf(archetype), matched, "predicate for $archetype")
        }
    }
}
