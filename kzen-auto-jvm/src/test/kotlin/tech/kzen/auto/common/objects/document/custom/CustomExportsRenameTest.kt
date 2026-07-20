package tech.kzen.auto.common.objects.document.custom

import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameDocumentRefactorCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectRefactorCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.SemanticNotationCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * A Custom document's `exports` list and the cross-document references that consume it are both Nominal, so a
 * rename must rewrite them. Assertions resolve the references (parse + locate) rather than comparing serialized
 * strings, so the pin covers the semantics without encoding the reference format.
 */
class CustomExportsRenameTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val customPath = DocumentPath.parse("test/custom-exports-rename-test.yaml")
    private val callerPath = DocumentPath.parse("test/custom-exports-rename-caller-test.yaml")

    private val exportedPath = ObjectPath.parse("main.objects/Exported")
    private val renamedPath = ObjectPath.parse("main.objects/Renamed")
    private val callerLocation = ObjectLocation(callerPath, ObjectPath.parse("main.steps/CallExported"))
    private val calleeAttribute = AttributeName("instructions")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun baselineResolvesBeforeAnyRename() {
        val notation = AutoTestUtils.readNotation()
        val exported = ObjectLocation(customPath, exportedPath)

        assertEquals(listOf(exported), exports(notation, customPath))
        assertEquals(exported, callee(notation))
        assertEquals(listOf(exported), exportedLogic(notation, customPath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun objectRenameRewritesExportsAndCrossDocumentReference() {
        val notation = applySemantic(RenameObjectRefactorCommand(
            ObjectLocation(customPath, exportedPath), ObjectName("Renamed")))

        val renamed = ObjectLocation(customPath, renamedPath)

        assertEquals(listOf(renamed), exports(notation, customPath))
        assertEquals(renamed, callee(notation))
        assertEquals(listOf(renamed), exportedLogic(notation, customPath))
    }


    /**
     * KNOWN GAP (recorded as EXT C7, not fixed here): kzen-lib's `adjustReferencesForRenamedDocument` only
     * rewrites references to a document's ROOT objects ("NB: only top-level (root) objects cross-document
     * reference are currently supported" — NotationReducerRefactor.kt). A Custom document's exports are nested
     * under `main.objects/`, so renaming the document leaves every cross-document consumer dangling
     * ("Missing: …#main.objects/Exported"). Object rename (above) is unaffected and green. Un-ignore once the
     * kzen-lib limitation is lifted.
     */
    @Ignore
    @Test
    fun documentRenameRewritesCrossDocumentReference() {
        val renamedDocumentPath = DocumentPath.parse("test/custom-exports-renamed-doc-test.yaml")

        val notation = applySemantic(RenameDocumentRefactorCommand(
            customPath, renamedDocumentPath.name))

        val relocated = ObjectLocation(renamedDocumentPath, exportedPath)

        // The exports list holds document-relative object paths, so it survives a document rename unchanged
        assertEquals(listOf(relocated), exports(notation, renamedDocumentPath))
        assertEquals(relocated, callee(notation))
        assertEquals(listOf(relocated), exportedLogic(notation, renamedDocumentPath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun applySemantic(command: SemanticNotationCommand): GraphNotation {
        val notation = AutoTestUtils.readNotation()
        return NotationReducer()
            .applySemantic(AutoTestUtils.graphDefinitionAttempt(notation), command)
            .graphNotation
    }


    private fun exports(notation: GraphNotation, documentPath: DocumentPath): List<ObjectLocation> {
        return CustomConventions.customDocumentExports(
            notation, documentPath, notation.documents[documentPath]!!)
    }


    private fun exportedLogic(notation: GraphNotation, documentPath: DocumentPath): List<ObjectLocation> {
        return CustomConventions.customDocumentExportedLogic(
            notation,
            AutoTestUtils.graphMetadata(notation),
            documentPath,
            notation.documents[documentPath]!!)
    }


    private fun callee(notation: GraphNotation): ObjectLocation {
        val scalar = notation.getString(callerLocation, calleeAttribute.asAttributePath())
        return notation.coalesce.locate(
            ObjectReference.parse(scalar),
            ObjectReferenceHost.ofLocation(callerLocation))
    }
}
