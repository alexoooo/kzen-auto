package tech.kzen.auto.server.service.impl

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.auto.server.util.awaitDone
import tech.kzen.auto.server.util.awaitState
import tech.kzen.lib.common.exec.engine.StepMode
import tech.kzen.lib.common.exec.logic.run.model.LogicRunFrameInfo
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
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
 * level deeper; Step Over / Step Out stay at / return to the caller level) plus the frame's "next to run"
 * position (so Step Over is distinguished from a no-op pause-before-Run: the child must have been consumed).
 *
 * Root steps: First (NumberLiteral) -> Run (RunStep -> child) -> Last (NumberLiteral).
 * Child steps: ChildA (NumberLiteral) -> ChildB (NumberLiteral).
 *
 * Now runs against the [tech.kzen.lib.server.exec.engine.RunEngine] that backs [ServerLogicController]: the
 * same Step Into / Over / Out behaviour is preserved, now driven by the engine's uniform checkpoint-before-step
 * boundaries and the engine-owned per-frame position (`checkpoint(at:)` — start+pause settles before the first
 * step; each step then advances exactly one).
 */
class StepNavigationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val rootPath = DocumentPath.parse("test/script/navigation/step-nav-test.yaml")
    private val childPath = DocumentPath.parse("test/script/navigation/step-nav-child-test.yaml")
    private val runFirstPath = DocumentPath.parse("test/script/navigation/step-nav-runfirst-test.yaml")
    private val foreachPath = DocumentPath.parse("test/script/control/script-engine-foreach-test.yaml")

    private val mainLocation = ObjectLocation(rootPath, ObjectPath.parse("main"))
    private val runLocation = ObjectLocation(rootPath, ObjectPath.parse("main.steps/Run"))
    private val lastLocation = ObjectLocation(rootPath, ObjectPath.parse("main.steps/Last"))
    private val childALocation = ObjectLocation(childPath, ObjectPath.parse("main.steps/ChildA"))
    private val childBLocation = ObjectLocation(childPath, ObjectPath.parse("main.steps/ChildB"))
    private val runFirstMainLocation = ObjectLocation(runFirstPath, ObjectPath.parse("main"))
    private val runFirstLastLocation = ObjectLocation(runFirstPath, ObjectPath.parse("main.steps/Last"))
    private val foreachMainLocation = ObjectLocation(foreachPath, ObjectPath.parse("main"))
    private val foreachLoopLocation = ObjectLocation(foreachPath, ObjectPath.parse("main.steps/Loop"))
    private val foreachBodyLocation = ObjectLocation(foreachPath, ObjectPath.parse("main.steps/Loop.steps/Doubled"))
    private val foreachTotalLocation = ObjectLocation(foreachPath, ObjectPath.parse("main.steps/Total"))

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
        context.serverLogicController.awaitDone()
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
        context.serverLogicController.awaitDone()
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
        context.serverLogicController.awaitDone()
    }


    @Test
    fun startStepIntoDescendsIntoAFirstRunStep() {
        // Baseline for the pair below: a plain stepping start (Into) whose FIRST step is a RunStep descends into
        // the child on the bootstrap step — pausing at the child's first step, depth 1. (This is the residual
        // "auto-step-over descends on the first tick" the Over start below fixes.)
        val controller = context.serverLogicController
        val runId = controller.start(runFirstMainLocation, snapshot)
            ?: fail("Unable to start run")

        controller.startStep(runId, StepMode.Into)
        controller.awaitState(LogicRunState.Paused)

        assertPausedAtDepth(1)
        assertNextToRun(runId, childALocation)

        resume(runId)
        controller.awaitDone()
    }


    @Test
    fun startStepOverRunsAFirstRunStepWithoutDescending() {
        // "Start Stepping Over": the bootstrap step is itself a Step Over, so a run whose first step enters a
        // child runs the whole child to completion and pauses before the next step (Last) at the root depth —
        // it does NOT descend (contrast Into's depth 1 above). This is what makes slow-motion auto-step-over
        // stay out of the sub-Script from the very first tick.
        val controller = context.serverLogicController
        val runId = controller.start(runFirstMainLocation, snapshot)
            ?: fail("Unable to start run")

        controller.startStep(runId, StepMode.Over)
        controller.awaitState(LogicRunState.Paused)

        assertPausedAtDepth(0)
        assertNextToRun(runId, runFirstLastLocation)

        resume(runId)
        controller.awaitDone()
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
        controller.awaitState(LogicRunState.Paused)

        assertPausedAtDepth(0)
        assertNextToRun(runId, runLocation)

        resume(runId)
        controller.awaitDone()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Inline-branch stepping is FRAME-only: a ForEach's body boundaries share the loop's frame (depth stays
    // 0 throughout), so step-over advances exactly one boundary — walking the loop body / iterations one at
    // a time — and step-out exits the DOCUMENT (only a hosted child, a deeper frame, runs free under Over).
    // Fixture: Range -> Loop (ForEach, body Doubled, 3 iterations) -> Total -> Result.

    @Test
    fun stepOverWalksForEachOneBoundaryAtATime() {
        val runId = startPausedAt(foreachMainLocation)

        step(runId)                              // run Range, pause before Loop
        assertPausedAtDepth(0)
        assertNextToRun(runId, foreachLoopLocation)

        stepOver(runId)                          // enter the loop, pause before Doubled (iteration 1)
        assertPausedAtDepth(0)                   // same frame — a branch has no frame of its own
        assertNextToRun(runId, foreachBodyLocation)

        stepOver(runId)                          // pause before Doubled (iteration 2)
        assertNextToRun(runId, foreachBodyLocation)

        stepOver(runId)                          // pause before Doubled (iteration 3)
        assertNextToRun(runId, foreachBodyLocation)

        stepOver(runId)                          // loop done, pause before Total
        assertPausedAtDepth(0)
        assertNextToRun(runId, foreachTotalLocation)

        resume(runId)
        context.serverLogicController.awaitDone()
    }


    @Test
    fun stepIntoForEachDescendsIntoBody() {
        val runId = startPausedAt(foreachMainLocation)

        step(runId)                              // run Range, pause before Loop
        step(runId)                              // step INTO Loop -> pause before Doubled (iteration 1)
        assertPausedAtDepth(0)                   // same frame — a branch has no frame of its own
        assertNextToRun(runId, foreachBodyLocation)

        resume(runId)
        context.serverLogicController.awaitDone()
    }


    @Test
    fun stepOutOfLoopBodyExitsDocument() {
        val runId = startPausedAt(foreachMainLocation)

        step(runId)                              // run Range, pause before Loop
        step(runId)                              // step INTO Loop -> pause before Doubled (iteration 1)
        assertNextToRun(runId, foreachBodyLocation)

        // Step-out exits the frame — the single-document run finishes (the loop's remaining iterations and
        // Total/Result run free); parking at a PARENT is pinned by the RunStep-based step-out test above.
        context.serverLogicController.stepOut(runId, snapshot)
        context.serverLogicController.awaitDone()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val snapshot: GraphDefinitionAttempt by lazy {
        AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())
    }


    private fun startPaused(): LogicRunId {
        return startPausedAt(mainLocation)
    }


    private fun startPausedAt(location: ObjectLocation): LogicRunId {
        val controller = context.serverLogicController
        val runId = controller.start(location, snapshot)
            ?: fail("Unable to start run")
        // Pause-at-entry: the run was created but never set running, so pause() lands paused immediately.
        controller.pause(runId)
        controller.awaitState(LogicRunState.Paused)
        return runId
    }


    private fun step(runId: LogicRunId) {
        context.serverLogicController.step(runId, snapshot)
        context.serverLogicController.awaitState(LogicRunState.Paused)
    }


    private fun stepOver(runId: LogicRunId) {
        context.serverLogicController.stepOver(runId, snapshot)
        context.serverLogicController.awaitState(LogicRunState.Paused)
    }


    private fun stepOut(runId: LogicRunId) {
        context.serverLogicController.stepOut(runId, snapshot)
        context.serverLogicController.awaitState(LogicRunState.Paused)
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
        val active = context.serverLogicController.status().active
            ?: fail("Run is not active")
        assertEquals(runId, active.id)
        val frame = frameForDocument(active.frame, expected.documentPath)
            ?: fail("No live frame for ${expected.documentPath}")
        assertEquals(expected, frame.position, "next to run")
    }


    // Deepest live frame showing the given document (matches the client's LogicRunFrames.frameForDocument).
    private fun frameForDocument(frame: LogicRunFrameInfo, documentPath: DocumentPath): LogicRunFrameInfo? {
        val deeper = frame.dependencies.firstNotNullOfOrNull { frameForDocument(it, documentPath) }
        return deeper ?: frame.takeIf { it.objectLocation.documentPath == documentPath }
    }


    private fun deepestDepth(frame: LogicRunFrameInfo): Int {
        if (frame.dependencies.isEmpty()) {
            return 0
        }
        return 1 + frame.dependencies.maxOf { deepestDepth(it) }
    }
}
