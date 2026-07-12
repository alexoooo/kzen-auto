package tech.kzen.auto.server.service.target

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.objects.document.target.TargetMatchPolicy
import tech.kzen.auto.server.service.target.test.CssSelectorTarget
import tech.kzen.auto.server.service.target.test.CssSelectorTargetLocator
import tech.kzen.auto.server.service.target.test.TargetSpecHolder
import tech.kzen.auto.server.service.target.test.TargetTestModule
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.media.MapNotationMedia
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


/**
 * The extensibility acceptance test for the open target-type set (target-improvements plan
 * phase 6): the CssSelector type — spec, notation handler, and locator all defined in the test
 * source set (see the `test` sub-package) — defines and creates through the real notation
 * machinery via its `is: TargetSpecType` registration, and locates through
 * [TargetLocator.register], with **no edit to any shared definer / creator / locator file**.
 */
class TargetExtensibilityTest {
    //-----------------------------------------------------------------------------------------------------------------
    private class StubDriver: RemoteWebDriver()


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun thirdPartyTargetTypeDefinesCreatesAndLocates() {
        TargetTestModule.register()

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val documentPath = DocumentPath.parse("test/target-extensibility-test.yaml")
        val holderLocation = ObjectLocation(documentPath, ObjectPath.parse("holder"))

        // Defines + creates through the shared (unedited) definer/creator dispatch
        val graphInstance = GraphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath))

        val holder = assertNotNull(graphInstance[holderLocation]).reference as TargetSpecHolder
        assertEquals(
            CssSelectorTarget("#login", TargetMatchPolicy.First),
            holder.target)

        // Locates through the shared (unedited) locator dispatch
        val targetLocator = TargetLocator(MapNotationMedia())
        targetLocator.register(CssSelectorTargetLocator)

        val result = runBlocking {
            targetLocator.locateElement(holder.target, StubDriver())
        }

        assertNull(result.error)
        assertNotNull(result.webElement)
        assertEquals("Matched CSS selector #login", result.note)
    }
}
