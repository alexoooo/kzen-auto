package tech.kzen.auto.server.service.impl

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.run.model.LogicRunFrameInfo
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail


/**
 * Regression coverage for the sidebar run indicator's stack-depth badge. Three Scripts call one another
 * via RunStep (nested-depth-test -> nested-depth-test-2 -> nested-depth-test-3, whose leaf is a PauseStep),
 * so the live frame tree from [ServerLogicController.status] must nest as a single spine
 * root -> child -> grandchild. The badge is derived client-side (LogicRunFrames.depthByDocument) as a
 * document's distance from the root, so a flat tree makes every nested document read depth 1.
 *
 * The pre-fix bug attached every guest frame directly under the root: the LogicHandle looked the host
 * frame up by the captured root execution id instead of the caller's execution id carried in
 * logicRunExecutionId, flattening the tree to root -> [child, grandchild] and pinning the badge at 1.
 */
class ServerLogicControllerFrameDepthTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val level1Path = DocumentPath.parse("test/nested-depth-test.yaml")
    private val level2Path = DocumentPath.parse("test/nested-depth-test-2.yaml")
    private val level3Path = DocumentPath.parse("test/nested-depth-test-3.yaml")
    private val level1 = ObjectLocation(level1Path, ObjectPath.parse("main"))

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
    fun nestedRunNestsFrameTreeByCallDepth() {
        val controller = context.serverLogicController

        // Snapshot the full notation (same path the other in-process logic tests use) and drive the run
        // off it, so the test is independent of graphStore classpath-scan wiring.
        val snapshot = graphDefinitionAttempt()

        val runId = controller.start(level1, snapshot)
            ?: fail("Unable to start run")
        controller.continueOrStart(runId, snapshot)
        awaitPaused(controller)

        val frame = assertNotNull(controller.status().active).frame

        // root -> child -> grandchild: exactly one dependency per level down to the paused leaf.
        assertEquals(
            listOf(level1Path, level2Path, level3Path),
            spine(frame).map { it.objectLocation.documentPath })

        // The deepest document is two frames below the root. Pre-fix the flattened tree made this 1.
        assertEquals(2, deepestDepth(frame))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun graphDefinitionAttempt(): GraphDefinitionAttempt {
        return AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())
    }


    private fun awaitPaused(controller: ServerLogicController) {
        for (attempt in 0 until 500) {
            if (controller.status().active?.state == LogicRunState.Paused) {
                return
            }
            Thread.sleep(10)
        }
        fail("Run did not reach the paused state")
    }


    // The single chain of frames from the root down: each level must have exactly one dependency until
    // the paused leaf. A flattened (buggy) tree breaks the chain at the first branching frame.
    private fun spine(frame: LogicRunFrameInfo): List<LogicRunFrameInfo> {
        val result = mutableListOf<LogicRunFrameInfo>()
        var current: LogicRunFrameInfo? = frame
        while (current != null) {
            result.add(current)
            current = current.dependencies.singleOrNull()
        }
        return result
    }


    private fun deepestDepth(frame: LogicRunFrameInfo): Int {
        if (frame.dependencies.isEmpty()) {
            return 0
        }
        return 1 + frame.dependencies.maxOf { deepestDepth(it) }
    }
}
