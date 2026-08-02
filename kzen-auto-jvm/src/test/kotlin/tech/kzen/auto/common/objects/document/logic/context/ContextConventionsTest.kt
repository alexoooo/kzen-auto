package tech.kzen.auto.common.objects.document.logic.context

import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.parse.YamlNotationParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/**
 * The `Context` archetype is not itself a Context. `GraphNotation.inheritanceChain` starts with the object
 * itself, so a whole-chain membership test matches the archetype against its own filter — which offered the
 * abstract base as a pickable declaration and gave the graph-wide duplicate-key check a keyless entry to alias
 * against. Membership is therefore a PROPER-ancestor test, pinned here for both the predicate and the picker
 * list built on it.
 */
class ContextConventionsTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()

    private val documentPath = DocumentPath.parse("test/context-conventions-test.yaml")
    private val declaredContext = ObjectLocation(documentPath, ObjectPath.parse("ProjectContext"))

    private val contextArchetype = ObjectLocation(
        DocumentPath.parse("auto-common/common-document.yaml"), ObjectPath.parse("Context"))


    private fun notation(): GraphNotation {
        return AutoTestUtils
            .readNotation()
            .withNewDocument(
                documentPath,
                DocumentNotation(yamlParser.parseDocumentObjects("""
                    ProjectContext:
                      abstract: true
                      is: Context
                      class: kotlin.String
                      key: project
                      title: "Project"
                """.trimIndent()), null))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun theArchetypeItselfIsNotAContext() {
        val notation = notation()

        assertTrue(contextArchetype in notation.coalesce,
            "fixture anchor: the Context archetype must be in the graph for this test to mean anything")
        assertFalse(ContextConventions.isContext(notation, contextArchetype))
        assertEquals(null, ContextConventions.descriptorOrNull(notation, contextArchetype))
    }


    @Test
    fun aConcreteDeclarationIsAContext() {
        val notation = notation()

        assertTrue(ContextConventions.isContext(notation, declaredContext))
        assertEquals("project", ContextConventions.descriptorOrNull(notation, declaredContext)?.key)
    }


    @Test
    fun allContextsHoldsTheDeclarationAndNotTheArchetype() {
        val locations = ContextConventions.allContexts(notation()).map { it.location }

        assertTrue(declaredContext in locations)
        assertFalse(contextArchetype in locations)
    }
}
