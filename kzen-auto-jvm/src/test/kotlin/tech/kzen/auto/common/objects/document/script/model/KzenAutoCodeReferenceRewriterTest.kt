package tech.kzen.auto.common.objects.document.script.model

import org.junit.Test
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
    private val documentPath = DocumentPath.parse("test/code-reference-rename-test.yaml")


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
    fun rewritesBacktickedReference() {
        val commands = rename("main.steps/My Source", "My Target")

        val backtickUser = location("main.steps/Backtick User")
        assertEquals(
            "`My Target` + 2",
            (commands.single { it.objectLocation == backtickUser }.attributeNotation as ScalarAttributeNotation).value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun rename(oldObjectPath: String, newName: String): List<UpdateInAttributeCommand> {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val oldLocation = location(oldObjectPath)
        val newLocation = oldLocation.copy(
            objectPath = oldLocation.objectPath.copy(name = ObjectName(newName)))

        return KzenAutoCodeReferenceRewriter.renameObjectReferences(
            oldLocation, newLocation, graphDefinitionAttempt)
    }


    private fun location(objectPath: String): ObjectLocation {
        return ObjectLocation(documentPath, ObjectPath.parse(objectPath))
    }
}
