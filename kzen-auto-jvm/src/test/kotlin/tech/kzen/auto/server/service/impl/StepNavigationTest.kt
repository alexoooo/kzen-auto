package tech.kzen.auto.server.service.impl

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.run.model.LogicRunFrameInfo
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail


/**
 * Characterization (regression-pinning) coverage for the controller's Step Into / Step Over / Step Out
 * across a nested Script (root [step-nav-test] runs child [step-nav-child-test] via RunStep). There was no
 * automated test for controller-level Step Over / Step Out before this; it pins the current behaviour so the
 * step-control simplification (replacing stepBudget + suppressPause + stepOut with budget + depthLimit) is
 * provably behaviour-preserving.
 *
 * The discriminator is the live frame-tree depth from [ServerLogicController.status] (Step Into descends one
 * level deeper; Step Over / Step Out stay at / return to the caller level) plus the published "next to run"
 * step (so Step Over is distinguished from a no-op pause-before-Run: the child must have been consumed).
 *
 * Root steps: First (NumberLiteral) -> Run (RunStep -> child) -> Last (NumberLiteral).
 * Child steps: ChildA (NumberLiteral) -> ChildB (NumberLiteral).
 *
 * Now runs against the [tech.kzen.lib.server.exec.engine.RunEngine] that backs [ServerLogicController]: the
 * same Step Into / Over / Out behaviour is preserved, now driven by the engine's uniform checkpoint-before-step
 * boundaries and the Script flavour's re-emitted `next-step` highlight (start+pause settles before the first
 * step; each step then advances exactly one).
 */
class StepNavigationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val rootPath = DocumentPath.parse("test/step-nav-test.yaml")
    private val childPath = DocumentPath.parse("test/step-nav-child-test.yaml")

    private val mainLocation = ObjectLocation(rootPath, ObjectPath.parse("main"))
    private val runLocation = ObjectLocation(rootPath, ObjectPath.parse("main.steps/Run"))
    private val lastLocation = ObjectLocation(rootPath, ObjectPath.parse("main.steps/Last"))
    private val childALocation = ObjectLocation(childPath, ObjectPath.parse("main.steps/ChildA"))
    private val childBLocation = ObjectLocation(childPath, ObjectPath.parse("main.steps/ChildB"))

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
    fun stepIntoDescendsIntoRunStepChild() {
        val runId = startPaused()

        step(runId)                              // run First, pause before Run
        assertPausedAtDepth(0)
        assertNextToRun(runId, runLocation)

        step(runId)                              // step INTO Run -> pause before child's first step
        assertPausedAtDepth(1)
        assertNextToRun(runId, childALocation)

        resume(runId)
        awaitDone()
    }


    @Test
    fun stepOverRunStepRunsChildToCompletion() {
        val runId = startPaused()

        step(runId)                              // run First, pause before Run
        assertPausedAtDepth(0)

        stepOver(runId)                          // run Run incl. the whole child, pause before Last
        assertPausedAtDepth(0)                   // did NOT descend (contrast Step Into's depth 1)
        assertNextToRun(runId, lastLocation)     // child was consumed: now paused before Last

        resume(runId)
        awaitDone()
    }


    @Test
    fun stepOutOfChildReturnsToCaller() {
        val runId = startPaused()

        step(runId)                              // run First, pause before Run
        step(runId)                              // step INTO Run -> pause before ChildA
        assertPausedAtDepth(1)
        step(runId)                              // run ChildA, pause before ChildB
        assertPausedAtDepth(1)
        assertNextToRun(runId, childBLocation)

        stepOut(runId)                           // run ChildB (rest of child), return to caller before Last
        assertPausedAtDepth(0)
        assertNextToRun(runId, lastLocation)

        resume(runId)
        awaitDone()
    }


    @Test
    fun startStepLaunchesAndRunsExactlyTheFirstStep() {
        val controller = context.serverLogicController
        val runId = controller.start(mainLocation, snapshot)
            ?: fail("Unable to start run")

        // "Start Stepping" (logicStartAndStep): launch the run AND run exactly its first step (First),
        // pausing before the second (Run) at the root depth. Regression: this was composed in the REST
        // handler as a racing pause() + step(), which tripped the controller guard with
        // "Can't step, already running" (pause-at-entry sets running before the synchronous step()).
        controller.startStep(runId)
        awaitState(LogicRunState.Paused)

        assertPausedAtDepth(0)
        assertNextToRun(runId, runLocation)

        resume(runId)
        awaitDone()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val snapshot: GraphDefinitionAttempt
        get() = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())


    private fun startPaused(): LogicRunId {
        val controller = context.serverLogicController
        val snapshot = snapshot
        val runId = controller.start(mainLocation, snapshot)
            ?: fail("Unable to start run")
        // Pause-at-entry: the run was created but never set running, so pause() lands paused immediately.
        controller.pause(runId)
        awaitState(LogicRunState.Paused)
        return runId
    }


    private fun step(runId: LogicRunId) {
        context.serverLogicController.step(runId, snapshot)
        awaitState(LogicRunState.Paused)
    }


    private fun stepOver(runId: LogicRunId) {
        context.serverLogicController.stepOver(runId, snapshot)
        awaitState(LogicRunState.Paused)
    }


    private fun stepOut(runId: LogicRunId) {
        context.serverLogicController.stepOut(runId, snapshot)
        awaitState(LogicRunState.Paused)
    }


    private fun resume(runId: LogicRunId) {
        context.serverLogicController.continueOrStart(runId, snapshot)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun assertPausedAtDepth(expectedDepth: Int) {
        val frame = (context.serverLogicController.status().active ?: fail("Run is not active")).frame
        assertEquals(expectedDepth, deepestDepth(frame), "paused frame depth")
    }


    private fun assertNextToRun(runId: LogicRunId, expected: ObjectLocation) {
        val snapshot = context.logicTraceStore.lookupRun(
            runId, LogicTraceQuery(LogicTracePath.root))
            ?: fail("No run trace")
        val nextStableId = snapshot.values[ScriptConventions.nextStepTracePath]?.value?.get()
            ?.let { ObjectStableId(it as String) }
        assertEquals(
            context.objectStableMapper.objectStableId(expected), nextStableId, "next to run")
    }


    private fun awaitState(state: LogicRunState) {
        for (attempt in 0 until 500) {
            if (context.serverLogicController.status().active?.state == state) {
                return
            }
            Thread.sleep(10)
        }
        fail("Run did not reach $state (was ${context.serverLogicController.status().active?.state})")
    }


    private fun awaitDone() {
        for (attempt in 0 until 500) {
            if (context.serverLogicController.status().active == null) {
                return
            }
            Thread.sleep(10)
        }
        fail("Run did not complete")
    }


    private fun deepestDepth(frame: LogicRunFrameInfo): Int {
        if (frame.dependencies.isEmpty()) {
            return 0
        }
        return 1 + frame.dependencies.maxOf { deepestDepth(it) }
    }
}
