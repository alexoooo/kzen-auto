package tech.kzen.auto.common.objects.document.logic.context

import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectRefactorCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.YamlNotationParser
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


/**
 * The Contexts document (`is: Contexts`) as a user-authorable home for declarations: a `by: NestedList` branch
 * of real objects at `main.contexts/<Name>`.
 *
 * The shape is the whole point of these tests. A declaration is a NOMINAL SYMBOL other documents name, and its
 * `ObjectLocation` is that identity — so it has to be an object, not an entry in a spec-payload list. Both
 * halves are pinned below: [nestedDeclarationsReachTheGraphWidePicker] (a nested entry is discovered by the
 * same inheritance filter as a first-party Context, with no registration anywhere) and
 * [renamingAUserDeclarationPropagatesIntoAConsumer] (rename-as-refactor reaches it, which a list item could
 * not offer — `renameObjectRefactor` asserts its target is in the coalesced OBJECT map).
 *
 * [aPlainNameDoesNotResolveAcrossDocumentsButTheObjectPathDoes] pins the resolution form the editors must
 * write, so the picker and the future SelectContextEditor do not have to rediscover it.
 */
class ContextsDocumentTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()

    private val contextsPath = DocumentPath.parse("test/context/contexts-document-test.yaml")
    private val consumerPath = DocumentPath.parse("test/context/contexts-consumer-test.yaml")

    private val alpha = ObjectLocation(contextsPath, ObjectPath.parse("main.contexts/Alpha"))
    private val beta = ObjectLocation(contextsPath, ObjectPath.parse("main.contexts/Beta"))
    private val renamedAlpha = ObjectLocation(contextsPath, ObjectPath.parse("main.contexts/Gamma"))

    private val contextArchetype = ObjectLocation(
        DocumentPath.parse("auto-common/common-document.yaml"), ObjectPath.parse("Context"))

    private val consumerMain = ObjectLocation(consumerPath, ObjectPath.parse("main"))


    // The reference string the editor mints for a cross-document Context (ContextSignatureEditor.referenceNameOf
    // tries the bare name first and falls back to this). Derived rather than spelled out, so the test pins the
    // BEHAVIOUR and not the reference syntax.
    private fun qualifiedReferenceTo(objectLocation: ObjectLocation): String =
        objectLocation.toReference().asString()


    private fun notation(exportReference: String = qualifiedReferenceTo(alpha)): GraphNotation {
        return AutoTestUtils
            .readNotation()
            .withNewDocument(
                contextsPath,
                DocumentNotation(yamlParser.parseDocumentObjects("""
                    main:
                      is: Contexts

                    main.contexts/Alpha:
                      is: Context
                      type:
                        class: kotlin.String
                        generics: []
                        nullable: false
                      qualifier: alpha
                      title: "Alpha"
                      description: "A user-declared context"

                    main.contexts/Beta:
                      is: Context
                      type:
                        class: kotlin.String
                        generics: []
                        nullable: false
                      qualifier: beta
                """.trimIndent()), null))
            .withNewDocument(
                consumerPath,
                DocumentNotation(yamlParser.parseDocumentObjects("""
                    main:
                      is: Script
                      context:
                        exports:
                          - $exportReference
                """.trimIndent()), null))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun nestedDeclarationsReachTheGraphWidePicker() {
        // No registration step exists, and none is wanted: allContexts iterates coalesce.map.keys, so a
        // declaration is pickable the moment the document holds it.
        val locations = ContextConventions.allContexts(notation()).map { it.location }

        assertContains(locations, alpha)
        assertContains(locations, beta)

        assertEquals(false, contextArchetype in locations,
            "the abstract base is filtered by the proper-ancestor test and must never be offered")
    }


    @Test
    fun aNestedDeclarationReadsItsContractLikeAnyOther() {
        val descriptor = ContextConventions.descriptorOrNull(notation(), alpha)

        assertNotNull(descriptor)
        assertEquals(TypeMetadata.string, descriptor.type)
        assertEquals("alpha", descriptor.qualifier)
        assertEquals("", descriptor.key, "no explicit key — the address derives from the canonical type")
        assertEquals("Alpha", descriptor.label())

        // The address a nested user declaration lands on is derived exactly as a first-party one's is.
        assertEquals("kotlin.String:alpha", ContextAddressing.keyOf(descriptor).asString())
    }


    @Test
    fun theDocumentIsRecognizedByItsMainArchetype() {
        val notation = notation()

        assertEquals(true, ContextConventions.isContextsDocument(notation.documents[contextsPath]!!))
        assertEquals(false, ContextConventions.isContextsDocument(notation.documents[consumerPath]!!),
            "a Script is not a Contexts document")
    }


    @Test
    fun renamingAUserDeclarationPropagatesIntoAConsumer() {
        val notation = notation()

        assertEquals(listOf(alpha),
            LogicContextConventions.documentExports(notation, consumerPath).map { it.location },
            "baseline: the consumer's export resolves to the nested declaration")

        val renamed = NotationReducer()
            .applySemantic(
                AutoTestUtils.graphDefinitionAttempt(notation),
                RenameObjectRefactorCommand(alpha, ObjectName("Gamma")))
            .graphNotation

        assertEquals(listOf(renamedAlpha),
            LogicContextConventions.documentExports(renamed, consumerPath).map { it.location },
            "a nested declaration is a real object, so rename-as-refactor rewrites what names it")

        assertNull(
            ContextConventions.resolveOrNull(renamed, qualifiedReferenceTo(alpha), consumerMain),
            "nothing resolves to the old name any more")
    }


    @Test
    fun aPlainNameDoesNotResolveAcrossDocumentsButTheObjectPathDoes() {
        // Pins the reference form the editors must write, because three forms are in play and only two of
        // them work. `referenceNameOf` tries the cropped form first and falls back to the full one, so what
        // actually lands in notation is the MIDDLE form — verified by a browser smoke, which wrote
        // `main.contexts/Greeting` into a Script's `context.exports`.
        val notation = notation()

        // 1. The plain object name. This is what a hand-written declaration naming a first-party Context
        //    looks like (`binds: BrowserContext`) — and it fails here, because a Contexts document's
        //    declaration is NESTED and resolution is relative to the referring document.
        assertNull(ContextConventions.resolveOrNull(notation, "Alpha", consumerMain))

        // 2. The object path without a document — `crop(retainPath = false)`, the form the editor writes.
        val objectPathReference = alpha.toReference().crop(retainPath = false).asString()
        assertEquals("main.contexts/Alpha", objectPathReference)
        assertEquals(
            alpha,
            ContextConventions.resolveOrNull(notation, objectPathReference, consumerMain)?.location,
            "the nesting is what disambiguates it; this is what ends up in notation")

        // 3. The fully-qualified reference — the fallback, correct but noisier.
        assertEquals(
            alpha,
            ContextConventions.resolveOrNull(notation, qualifiedReferenceTo(alpha), consumerMain)?.location)
    }
}
