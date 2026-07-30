package tech.kzen.auto.server.service.impl

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.script.model.ForEachProgress
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.script.test.CountingStep
import tech.kzen.auto.server.exec.script.test.ScriptStepTestModule
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunResponse
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * End-to-end move-to (Set Next Statement) coverage (execution-control phase XC2) driven through the real
 * [ServerLogicController] + [tech.kzen.lib.server.exec.engine.RunEngine]: backward re-run, forward skip (and the
 * value backstop), the no-op frontier jump, jump after terminal, a loop-step restart, a loop-body rejection,
 * an If-branch descend (both into the first branch and into a later one, whose path crosses a structural branch
 * group node), and a backward jump past a completed RunStep, which must ABANDON the sub-Script invocation it
 * hosted rather than let the re-hosted one adopt its capture. Each fixture uses the test-only [CountingStep] so
 * re-execution is observable via the process-global count; the suite resets it per test and relies on sequential
 * execution (as the sibling static-fixture engine tests do).
 */
class ScriptMoveToTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val linearPath = DocumentPath.parse("test/script-moveto-test.yaml")
    private val backstopPath = DocumentPath.parse("test/script-moveto-backstop-test.yaml")
    private val loopPath = DocumentPath.parse("test/script-moveto-loop-test.yaml")
    private val ifPath = DocumentPath.parse("test/script-moveto-if-test.yaml")
    private val elseIfPath = DocumentPath.parse("test/script-moveto-elseif-test.yaml")
    private val abandonPath = DocumentPath.parse("test/script-moveto-abandon-test.yaml")

    private lateinit var context: KzenAutoContext


    //-----------------------------------------------------------------------------------------------------------------
    @Before
    fun setUp() {
        ScriptStepTestModule.register()
        CountingStep.reset()
        context = KzenAutoContext.forTest()
    }


    @After
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun backwardJumpReRunsFromTarget() {
        val runId = startPaused(linearPath)      // before A
        step(runId)                              // A, before B
        step(runId)                              // B, before C
        step(runId)                              // C, before D
        assertEquals(3, CountingStep.count.get())

        assertEquals(LogicRunResponse.Submitted, moveTo(runId, linearPath, "main.steps/B"))
        awaitState(LogicRunState.Paused)
        assertNextToRun(runId, ObjectLocation(linearPath, ObjectPath.parse("main.steps/B")))
        assertEquals(3, CountingStep.count.get())   // the jump itself runs nothing

        resume(runId)
        awaitDone()
        assertEquals(6, CountingStep.count.get())   // B, C, D re-ran; A (kept) did not
    }


    @Test
    fun forwardJumpSkipsInterveningSteps() {
        val runId = startPaused(linearPath)      // before A
        step(runId)                              // A, before B
        assertEquals(1, CountingStep.count.get())

        assertEquals(LogicRunResponse.Submitted, moveTo(runId, linearPath, "main.steps/D"))
        awaitState(LogicRunState.Paused)
        assertNextToRun(runId, ObjectLocation(linearPath, ObjectPath.parse("main.steps/D")))
        assertEquals(1, CountingStep.count.get())   // B, C skipped (not run)

        resume(runId)
        awaitDone()
        assertEquals(2, CountingStep.count.get())   // only A and D ran
    }


    @Test
    fun noOpJumpToTheFrontierRunsNothing() {
        val runId = startPaused(linearPath)      // parked before A (the frontier)
        assertEquals(LogicRunResponse.Submitted, moveTo(runId, linearPath, "main.steps/A"))
        assertNextToRun(runId, ObjectLocation(linearPath, ObjectPath.parse("main.steps/A")))
        assertEquals(0, CountingStep.count.get())
    }


    @Test
    fun jumpAfterTerminalReturnsNotFound() {
        val runId = startPaused(linearPath)
        resume(runId)
        awaitDone()
        assertEquals(LogicRunResponse.NotFound, moveTo(runId, linearPath, "main.steps/A"))
    }


    @Test
    fun forwardJumpPastAReferencedStepErrorParks() {
        val runId = startPaused(backstopPath, pauseOnError = true)   // before A
        step(runId)                              // A, before B

        // Skip B (value-less), park before C; C references B, so on resume it hits the "No value produced"
        // backstop and error-parks (decision 2). awaitState fails the test if ErrorPaused is never reached.
        assertEquals(LogicRunResponse.Submitted, moveTo(runId, backstopPath, "main.steps/C"))
        awaitState(LogicRunState.Paused)
        resume(runId)
        awaitState(LogicRunState.ErrorPaused)
    }


    @Test
    fun loopBodyTargetIsRejected() {
        val runId = startPaused(loopPath)        // before Range
        assertEquals(
            LogicRunResponse.Rejected,
            moveTo(runId, loopPath, "main.steps/Loop.steps/Body"))
    }


    @Test
    fun jumpToLoopStepRestartsIt() {
        val runId = startPaused(loopPath)        // before Range
        step(runId)                              // Range, before Loop
        stepOver(runId)                          // enter loop, before Body iteration 1
        stepOver(runId)                          // before Body iteration 2 (iter 1 body ran)
        stepOver(runId)                          // before Body iteration 3 (iter 2 body ran)
        stepOver(runId)                          // loop done, before Total (iter 3 body ran)
        assertEquals(3, CountingStep.count.get())

        assertEquals(LogicRunResponse.Submitted, moveTo(runId, loopPath, "main.steps/Loop"))
        awaitState(LogicRunState.Paused)
        assertNextToRun(runId, ObjectLocation(loopPath, ObjectPath.parse("main.steps/Loop")))
        assertEquals(3, CountingStep.count.get())

        resume(runId)
        awaitDone()
        assertEquals(6, CountingStep.count.get())   // the loop restarted at iteration 0: 3 more body runs
    }


    @Test
    fun forwardJumpPastAMidFlightLoopCommitsItsPartialValue() {
        val runId = startPaused(loopPath, pauseOnError = true)   // before Range
        step(runId)                              // Range, before Loop
        stepOver(runId)                          // enter loop, before Body iteration 1
        stepOver(runId)                          // before Body iteration 2 (iteration 1 collected 1)
        stepOver(runId)                          // before Body iteration 3 (iteration 2 collected 2)
        assertEquals(2, CountingStep.count.get())

        // Total reads `Loop.sum()`. The loop is mid-flight, so rather than being skipped value-less (which would
        // error-park Total on the "No value produced" backstop) it commits the iterations it had collected.
        assertEquals(LogicRunResponse.Submitted, moveTo(runId, loopPath, "main.steps/Total"))
        awaitState(LogicRunState.Paused)
        assertNextToRun(runId, ObjectLocation(loopPath, ObjectPath.parse("main.steps/Total")))

        val loopTrace = stepTrace(runId, loopPath, "main.steps/Loop")
            ?: fail("No loop trace")
        assertEquals(StepTrace.State.Done, loopTrace.state, "the committed loop reads as done, not skipped")

        val progress = ForEachProgress.ofExecutionValueOrNull(loopTrace.detail)
            ?: fail("The committed value must keep the loop's journal, not blank it")
        assertTrue(progress.partial, "committed short of a full run")
        assertEquals(listOf("1" to "1", "2" to "2"), progress.produced.map { it.item to it.value })

        resume(runId)
        awaitDone()

        assertEquals(2, CountingStep.count.get(), "the third iteration never ran")
        assertEquals(
            "3", stepTrace(runId, loopPath, "main.steps/Total")?.displayValue?.get(),
            "Total summed the committed partial [1, 2]")
    }


    @Test
    fun jumpIntoAnIfBranchDescendsAndParks() {
        val runId = startPaused(ifPath)          // before Flag
        step(runId)                              // Flag, before Gate
        step(runId)                              // into Gate (condition true), before T1
        step(runId)                              // T1, before T2
        step(runId)                              // T2, before After (Gate completes)
        assertEquals(2, CountingStep.count.get())

        // Backward into the If branch: Gate re-runs its condition (descend, checkpoint suppressed), T1 is
        // adopted (kept), and the run parks at T2 — nothing re-executes yet.
        assertEquals(LogicRunResponse.Submitted, moveTo(runId, ifPath, "main.steps/Gate.branches/Branch.steps/T2"))
        awaitState(LogicRunState.Paused)
        assertNextToRun(runId, ObjectLocation(ifPath, ObjectPath.parse("main.steps/Gate.branches/Branch.steps/T2")))
        assertEquals(2, CountingStep.count.get())

        resume(runId)
        awaitDone()
        assertEquals(3, CountingStep.count.get())   // only T2 re-ran; T1 (kept) did not
    }


    @Test
    fun jumpIntoASecondIfBranchDescendsThroughTheGroupAndParks() {
        // Same descend contract one level deeper: the target lives in the SECOND branch of an if/else-if chain,
        // so the path to it runs through an IfBranch group node. Only Gate is a real container step, so only
        // Gate may be in the descend set — a group node in there would ask the spine to "run" a notation object
        // that has no execution.
        val runId = startPaused(elseIfPath)       // before Off
        step(runId)                               // Off, before On
        step(runId)                               // On, before Gate
        step(runId)                               // into Gate (branch 1 false, branch 2 true), before B1
        step(runId)                               // B1, before B2
        step(runId)                               // B2, before After (Gate completes)
        assertEquals(2, CountingStep.count.get())  // B1 + B2; branch 1's A1 never ran

        assertEquals(
            LogicRunResponse.Submitted,
            moveTo(runId, elseIfPath, "main.steps/Gate.branches/Branch 2.steps/B2"))
        awaitState(LogicRunState.Paused)
        assertNextToRun(
            runId,
            ObjectLocation(elseIfPath, ObjectPath.parse("main.steps/Gate.branches/Branch 2.steps/B2")))
        assertEquals(2, CountingStep.count.get())

        resume(runId)
        awaitDone()
        assertEquals(3, CountingStep.count.get())  // only B2 re-ran; B1 (kept) and A1 (not taken) did not
    }


    @Test
    fun backwardJumpPastACompletedRunStepAbandonsItsChildInvocation() {
        val runId = startPaused(abandonPath)     // before Before
        step(runId)                              // Before, before Call
        stepOver(runId)                          // Call ran to completion (hosting the child), before After
        assertEquals(2, CountingStep.count.get(), "Before, plus the child's Inner")

        // Backward PAST the completed RunStep: its outcome is in the drop set, so the rebuilt spine re-runs it
        // and re-hosts the child. The child's pre-jump invocation is ABANDONED — its migration capture must be
        // discarded, or the fresh invocation adopts it, replay-short-circuits every step, and the sub-Script
        // "re-runs" instantaneously while returning its pre-jump values.
        assertEquals(LogicRunResponse.Submitted, moveTo(runId, abandonPath, "main.steps/Before"))
        awaitState(LogicRunState.Paused)
        assertNextToRun(runId, ObjectLocation(abandonPath, ObjectPath.parse("main.steps/Before")))
        assertEquals(2, CountingStep.count.get(), "the jump itself runs nothing")

        resume(runId)
        awaitDone()

        // Before re-runs (3), the re-hosted child's Inner re-runs (4), After runs (5). Without the discard the
        // child replay-adopts instead of executing and the count stops at 4.
        assertEquals(
            5, CountingStep.count.get(),
            "the re-hosted sub-Script must EXECUTE its steps, not replay-adopt the abandoned invocation's capture")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val snapshot: GraphDefinitionAttempt
        get() = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())


    private fun startPaused(documentPath: DocumentPath, pauseOnError: Boolean = false): LogicRunId {
        val controller = context.serverLogicController
        val main = ObjectLocation(documentPath, ObjectPath.parse("main"))
        val runId = controller.startAttempt(main, snapshot, pauseOnError).runIdOrNull
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


    private fun resume(runId: LogicRunId) {
        context.serverLogicController.continueOrStart(runId, snapshot)
    }


    private fun moveTo(runId: LogicRunId, documentPath: DocumentPath, objectPath: String): LogicRunResponse {
        return context.serverLogicController.moveTo(
            runId, ObjectLocation(documentPath, ObjectPath.parse(objectPath)), snapshot)
    }


    private fun stepTrace(runId: LogicRunId, documentPath: DocumentPath, objectPath: String): StepTrace? {
        val snapshot = context.logicTrace.lookupRun(runId, LogicTraceQuery(LogicTracePath.root))
            ?: return null
        val stableId = context.objectStableMapper.objectStableId(
            ObjectLocation(documentPath, ObjectPath.parse(objectPath)))
        val entry = snapshot.values[LogicTracePath.ofObjectStableId(stableId)]
            ?: return null
        return StepTrace.ofExecutionValue(entry.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun assertNextToRun(runId: LogicRunId, expected: ObjectLocation) {
        val active = context.serverLogicController.status().active
            ?: fail("Run is not active")
        assertEquals(runId, active.id)
        assertEquals(expected, active.frame.position, "next to run")
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
}
