package tech.kzen.auto.common.objects.document.logic.context

import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectRefactorCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.YamlNotationParser
import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * Renaming a Context rewrites the declarations that name it — the behaviour the `by: Nominal` meta
 * declarations buy: weak references are walked by the refactor's reference scan, so a declaration keeps
 * naming the Context it named rather than dangling. One fixture document covers all three declaration
 * shapes: the `context.exports` map-of-lists on `main`, a step's scalar `provides`, and a step's list
 * `requires`. The document-level shape is the load-bearing one — the `context` map's meta is open-keyed, so
 * every signature key rides on that one declaration and none is enumerated anywhere.
 *
 * Scope note: the scan rewrites references held by DEFINED objects. An `abstract: true` archetype (e.g.
 * `RequireContextTestStep` itself) has no definition, so a rename does not reach the archetype's own
 * declaration — first-party Contexts live in read-only classpath notation and are never renamed, so the case
 * that matters is exactly this one: a user's concrete document naming a Context.
 */
class ContextRenameTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()

    private val documentPath = DocumentPath.parse("test/context-rename-test.yaml")

    private val archetypesPath = DocumentPath.parse("test/script-step-test-archetypes.yaml")
    private val originalContext = ObjectLocation(archetypesPath, ObjectPath.parse("TestSutContext"))
    private val renamedContext = ObjectLocation(archetypesPath, ObjectPath.parse("RenamedSutContext"))

    private val mainLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
    private val provideLocation = ObjectLocation(documentPath, ObjectPath.parse("main.steps/Provide"))
    private val readLocation = ObjectLocation(documentPath, ObjectPath.parse("main.steps/Read"))


    private fun notation(): GraphNotation {
        return AutoTestUtils
            .readNotation()
            .withNewDocument(
                documentPath,
                DocumentNotation(yamlParser.parseDocumentObjects("""
                    main:
                      is: Script
                      context:
                        exports:
                          - TestSutContext

                    main.steps/Provide:
                      is: ProvideContextTestStep
                      provides: TestSutContext
                      value: "probe"

                    main.steps/Read:
                      is: RequireContextTestStep
                      requires:
                        - TestSutContext
                """.trimIndent()), null))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun baselineResolvesBeforeAnyRename() {
        val notation = notation()

        assertEquals(listOf(originalContext),
            LogicContextConventions.documentExports(notation, documentPath).map { it.location })
        assertEquals(originalContext,
            LogicContextConventions.stepProvides(notation, provideLocation)?.location)
        assertEquals(listOf(originalContext),
            LogicContextConventions.stepRequires(notation, readLocation).map { it.location })
    }


    @Test
    fun renamingAContextRewritesExportsProvidesAndRequires() {
        val notation = notation()

        val renamed = NotationReducer()
            .applySemantic(
                AutoTestUtils.graphDefinitionAttempt(notation),
                RenameObjectRefactorCommand(originalContext, ObjectName("RenamedSutContext")))
            .graphNotation

        assertEquals(listOf(renamedContext),
            LogicContextConventions.documentExports(renamed, documentPath).map { it.location },
            "context.exports (map-of-lists shape) must follow the rename")
        assertEquals(renamedContext,
            LogicContextConventions.stepProvides(renamed, provideLocation)?.location,
            "scalar provides must follow the rename")
        assertEquals(listOf(renamedContext),
            LogicContextConventions.stepRequires(renamed, readLocation).map { it.location },
            "list requires must follow the rename")

        // sanity: nothing resolves to the old name any more from this document
        assertEquals(null, ContextConventions.resolveOrNull(renamed, "TestSutContext", mainLocation))
    }
}
