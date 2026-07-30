package tech.kzen.auto.server.service.impl

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * A refused start must say WHY: the reason is served as the 400 body, so the browser can show it instead of the
 * failure living only in the server log.
 */
class ServerLogicControllerStartFailureTest {
    //-----------------------------------------------------------------------------------------------------------------
    // An object that exists and defines, but isn't a runnable Logic document.
    private val notRunnable = ObjectLocation(
        DocumentPath.parse("test/detached-cache-test.yaml"),
        ObjectPath.parse("CacheNamed"))

    private val runnable = ObjectLocation(
        DocumentPath.parse("test/script/engine/nested-depth-test.yaml"),
        ObjectPath.parse("main"))

    private lateinit var context: KzenAutoContext


    //-----------------------------------------------------------------------------------------------------------------
    @Before
    fun setUp() {
        context = KzenAutoContext.forTest()
    }


    @After
    fun tearDown() {
        context.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun compileFailureNamesTheRootAndTheCause() {
        val attempt = context.serverLogicController.startAttempt(notRunnable, snapshot(), false)

        val failed = assertIs<LogicStartAttempt.Failed>(attempt)
        assertTrue(failed.reason.contains(notRunnable.asString()), failed.reason)
        assertTrue(failed.reason.contains("Logic"), failed.reason)
    }


    @Test
    fun compileFailureStillReadsAsNoRunThroughTheControllerContract() {
        assertNull(context.serverLogicController.start(notRunnable, snapshot()))
    }


    @Test
    fun secondStartIsRefusedWhileARunIsInProgress() {
        val controller = context.serverLogicController
        val snapshot = snapshot()

        assertIs<LogicStartAttempt.Started>(
            controller.startAttempt(runnable, snapshot, false))

        val failed = assertIs<LogicStartAttempt.Failed>(
            controller.startAttempt(runnable, snapshot, false))
        assertEquals("A run is already in progress", failed.reason)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun snapshot(): GraphDefinitionAttempt {
        return AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())
    }
}
