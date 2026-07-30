package tech.kzen.auto.common.objects.document.script.model

import org.junit.Test
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class KzenAutoCodeReferenceRewriterTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/script/structure/code-reference-rename-test.yaml")
    private val ifDocumentPath = DocumentPath.parse("test/script/structure/code-reference-rename-if-test.yaml")
    private val forEachDocumentPath = DocumentPath.parse("test/script/structure/code-reference-rename-foreach-test.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun rewritesPlainIdentifierReference() {
        val commands = rename("main.steps/Source", "Renamed")

        // Derived's `Source + 1` is rewritten in scope...
        val derived = location("main.steps/Derived")
        assertEquals(
            "Renamed + 1",
            (commands.single { it.objectLocation == derived }.attributeNotation as ScalarAttributeNotation).value)

        // ...but "Source" inside a string literal is left alone.
        val stringLiteral = location("main.steps/String Literal")
        assertTrue(commands.none { it.objectLocation == stringLiteral })
    }


    @Test
    fun rewritesAnIfBranchCondition() {
        val commands = rename(ifDocumentPath, "main.steps/Source", "Renamed")

        // The branch condition is an ordinary value scalar, so it rewrites exactly like a Formula's `code`
        // — a reference-typed attribute would be skipped here instead. Both branch conditions AND the
        // in-branch step's code are in scope of the renamed root step.
        assertEquals(
            "Renamed > 0",
            rewritten(commands, ifLocation("main.steps/Gate.branches/Branch")))
        assertEquals(
            "Renamed < 0",
            rewritten(commands, ifLocation("main.steps/Gate.branches/Branch 2")))
        assertEquals(
            "Renamed + 1",
            rewritten(commands, ifLocation("main.steps/Gate.branches/Branch.steps/Guarded")))

        // The string literal in the other branch is still left alone.
        val untouched = ifLocation("main.steps/Gate.branches/Branch 2.steps/Untouched")
        assertTrue(commands.none { it.objectLocation == untouched })
    }


    @Test
    fun rewritesAForEachItemsExpression() {
        val commands = rename(forEachDocumentPath, "main.steps/Source", "Renamed")

        // `items` is a value scalar now, so it rewrites like a Formula's `code`; while it was a reference
        // attribute this rewriter skipped it entirely and kzen-lib adjusted the path instead.
        val items = commands.single { it.objectLocation == forEachLocation("main.steps/Outer") }
        assertEquals(ScriptConventions.itemsAttributePath, items.attributePath)
        assertEquals("1..Renamed", (items.attributeNotation as ScalarAttributeNotation).value)

        // The string literal in the loop body is still left alone.
        val untouched = forEachLocation("main.steps/Outer.steps/Inner.steps/Untouched")
        assertTrue(commands.none { it.objectLocation == untouched })
    }


    @Test
    fun rewritesAnInnerLoopsItemsWhenTheOuterItemIsRenamed() {
        // The item binding is in scope for the inner loop's items expression, so renaming it must rewrite
        // the range that reads it — the same scope (ScriptTree.inScopeReferencePaths) the server compiles
        // that expression against.
        val commands = rename(forEachDocumentPath, "main.steps/Outer.item/Outer Item", "Element")

        assertEquals(
            "1..Element",
            rewritten(commands, forEachLocation("main.steps/Outer.steps/Inner")))
    }


    @Test
    fun rewritesBacktickedReference() {
        val commands = rename("main.steps/My Source", "My Target")

        val backtickUser = location("main.steps/Backtick User")
        assertEquals(
            "`My Target` + 2",
            (commands.single { it.objectLocation == backtickUser }.attributeNotation as ScalarAttributeNotation).value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun rename(oldObjectPath: String, newName: String): List<UpdateInAttributeCommand> {
        return rename(documentPath, oldObjectPath, newName)
    }


    private fun rename(
        inDocumentPath: DocumentPath,
        oldObjectPath: String,
        newName: String
    ): List<UpdateInAttributeCommand> {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val oldLocation = ObjectLocation(inDocumentPath, ObjectPath.parse(oldObjectPath))
        val newLocation = oldLocation.copy(
            objectPath = oldLocation.objectPath.copy(name = ObjectName(newName)))

        return KzenAutoCodeReferenceRewriter.renameObjectReferences(
            oldLocation, newLocation, graphDefinitionAttempt)
    }


    private fun rewritten(
        commands: List<UpdateInAttributeCommand>,
        objectLocation: ObjectLocation
    ): String {
        return (commands.single { it.objectLocation == objectLocation }
            .attributeNotation as ScalarAttributeNotation).value
    }


    private fun location(objectPath: String): ObjectLocation {
        return ObjectLocation(documentPath, ObjectPath.parse(objectPath))
    }


    private fun ifLocation(objectPath: String): ObjectLocation {
        return ObjectLocation(ifDocumentPath, ObjectPath.parse(objectPath))
    }


    private fun forEachLocation(objectPath: String): ObjectLocation {
        return ObjectLocation(forEachDocumentPath, ObjectPath.parse(objectPath))
    }
}
