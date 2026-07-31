package tech.kzen.auto.server.service.impl

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.script.test.CountingStep
import tech.kzen.auto.server.exec.script.test.ScriptStepTestModule
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunFrameInfo
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.fail


/**
 * Move-to (Set Next Statement) addressed to a NESTED frame — the call-site-path half of the repositioning
 * contract (logic-spec §4), which [ScriptMoveToTest] covers only at its empty-path (root frame) degenerate case.
 * Driven through the same real [ServerLogicController] + [tech.kzen.lib.server.exec.engine.RunEngine] stack:
 * backward re-run and forward skip inside a hosted sub-Script, an If-branch descend composed with the frame
 * descent (the transit frame suppresses the hosting RunStep's boundary, the addressed frame additionally
 * suppresses its own IfStep's), a middle-frame jump that must ABANDON the deeper invocation, a deepest-frame
 * jump whose call-site path claims two transit hops, LOOP-HOSTED transit through each loop archetype and
 * through two nested loops at once, and the three
 * refusal gates — a target whose document holds no live frame, a frame that has already settled, and a transit
 * hop whose Logic is not [tech.kzen.lib.common.exec.engine.Repositionable].
 *
 * The loop-hosted cases are what separates the two repositioning roles: a loop body is not a legal jump TARGET
 * ([ScriptMoveToTest.loopBodyTargetIsRejected]) yet a call-site inside one carries a descent, because the
 * rebuilt loop re-enters at its carried cursor and the descent rides that resumed iteration rather than a
 * position the analysis had to invent.
 *
 * Every positive case also pins the position of each TRANSIT frame it passes through, because a descent reaches
 * its call-site with the boundary suppressed — and a named boundary is the only thing that ever writes a frame's
 * position, which starts null on each rebuild. Left unwritten, the hosting document reports no "element about to
 * run" at all: no next-step highlight, and no move-to drag handle, since the handle IS that marker.
 *
 * Execution is read off [CountingStep]'s process-global count, which the suite resets per test: it distinguishes
 * a step that actually RAN from one that replay-adopted a carried outcome (the failure mode a nested jump
 * invites — an abandoned sub-Script invocation whose capture the re-hosted one adopts returns its pre-jump
 * values instantly). Frames are named by walking the live frame tree ([deepestFrame]); the client's own
 * `LogicRunFrames.frameForDocument` is jsMain-only.
 *
 * Self-recursion is covered only as far as ADDRESSING: two simultaneously live frames of one self-hosting
 * document are separately nameable and a jump addressed to the deeper one is accepted. Which frame then moves is
 * deliberately unasserted — [tech.kzen.lib.server.exec.engine.RunEngine]'s migration capture register is keyed by
 * [tech.kzen.lib.common.service.store.normal.ObjectStableId] alone, so the two frames collide on one key with no
 * defined winner (logic-spec §5) — the loser rebuilds with no carried outcomes and re-runs from its own first
 * step, which is observationally identical to it having wrongly claimed the other frame's jump. No assertion
 * separates the two, so an outcome pinned here would be undefined behaviour promoted to a contract.
 */
class ScriptNestedMoveToTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val nestedPath = DocumentPath.parse("test/script/navigation/script-moveto-nested-test.yaml")
    private val nestedChildPath = DocumentPath.parse("test/script/navigation/script-moveto-nested-child-test.yaml")
    private val chainPath = DocumentPath.parse("test/script/navigation/script-moveto-chain-test.yaml")
    private val chainBPath = DocumentPath.parse("test/script/navigation/script-moveto-chain-b-test.yaml")
    private val chainCPath = DocumentPath.parse("test/script/navigation/script-moveto-chain-c-test.yaml")
    private val transitPath = DocumentPath.parse("test/script/navigation/script-moveto-transit-test.yaml")
    private val transitChildPath =
        DocumentPath.parse("test/script/navigation/script-moveto-transit-child-test.yaml")
    private val recursivePath = DocumentPath.parse("test/script/navigation/script-moveto-recursive-test.yaml")

    private val loopTransitPath = DocumentPath.parse("test/script/navigation/script-moveto-loop-transit-test.yaml")
    private val loopTransitChildPath =
        DocumentPath.parse("test/script/navigation/script-moveto-loop-transit-child-test.yaml")
    private val doWhileTransitPath =
        DocumentPath.parse("test/script/navigation/script-moveto-dowhile-transit-test.yaml")
    private val doWhileTransitChildPath =
        DocumentPath.parse("test/script/navigation/script-moveto-dowhile-transit-child-test.yaml")
    private val nestedLoopTransitPath =
        DocumentPath.parse("test/script/navigation/script-moveto-nested-loop-transit-test.yaml")

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
    fun backwardJumpInsideASubScriptFrameReRunsFromTarget() {
        val runId = startPaused(nestedPath)      // before Before
        step(runId)                              // Before, before Call
        step(runId)                              // into the child, before C1
        step(runId)                              // C1, before C2
        step(runId)                              // C2, before On
        assertEquals(3, CountingStep.count.get(), "Before, C1, C2")

        assertEquals(
            LogicRunResponse.Submitted,
            moveTo(runId, nestedChildPath, "main.steps/C1", frameFor(nestedChildPath)))
        awaitState(LogicRunState.Paused)
        assertFrameNextToRun(nestedChildPath, "main.steps/C1")
        assertFrameNextToRun(nestedPath, "main.steps/Call")
        assertEquals(3, CountingStep.count.get(), "the jump itself runs nothing")

        resume(runId)
        awaitDone()

        // The child re-ran C1, C2, G1, C3 (4..7) and the parent then ran After (8). The parent is the TRANSIT
        // frame: it re-hosts the child through a suppressed Call boundary but repositions nothing of its own, so
        // Before keeps its pre-jump outcome and After runs exactly once, last.
        assertEquals(8, CountingStep.count.get())
        assertEquals(
            "1", stepDisplay(runId, nestedPath, "main.steps/Before"),
            "the parent's Before was adopted, not re-run")
        assertEquals(
            "8", stepDisplay(runId, nestedPath, "main.steps/After"),
            "the parent's After ran once, after the repositioned child returned")
    }


    @Test
    fun forwardJumpInsideASubScriptFrameSkipsInterveningSteps() {
        val runId = startPaused(nestedPath)      // before Before
        step(runId)                              // Before, before Call
        step(runId)                              // into the child, before C1
        step(runId)                              // C1, before C2
        assertEquals(2, CountingStep.count.get(), "Before, C1")

        assertEquals(
            LogicRunResponse.Submitted,
            moveTo(runId, nestedChildPath, "main.steps/C3", frameFor(nestedChildPath)))
        awaitState(LogicRunState.Paused)
        assertFrameNextToRun(nestedChildPath, "main.steps/C3")
        assertFrameNextToRun(nestedPath, "main.steps/Call")
        assertEquals(2, CountingStep.count.get(), "C2, On and Gate are skipped, not run")

        resume(runId)
        awaitDone()

        // Only C3 (3) and the parent's After (4): the skipped Gate never opened its branch, so G1 never ran.
        assertEquals(4, CountingStep.count.get())
        assertEquals(
            StepTrace.State.Skipped, stepTrace(runId, nestedChildPath, "main.steps/C2")?.state,
            "the walk passed over C2 value-less")
        assertEquals("1", stepDisplay(runId, nestedPath, "main.steps/Before"), "the parent did not move")
    }


    @Test
    fun jumpIntoAnIfBranchInsideASubScriptFrameDescendsThroughBothFrames() {
        val runId = startPaused(nestedPath)      // before Before
        step(runId)                              // Before, before Call
        step(runId)                              // into the child, before C1
        step(runId)                              // C1, before C2
        step(runId)                              // C2, before On
        step(runId)                              // On, before Gate
        step(runId)                              // into Gate (condition true), before G1
        step(runId)                              // G1, before C3 (Gate completes)
        assertEquals(4, CountingStep.count.get(), "Before, C1, C2, G1")

        // Two suppressions compose on one rebuild: the parent runs to Call without parking (transit descent),
        // and the child then runs Gate — re-evaluating its condition — without parking, to park at G1 inside
        // the branch.
        assertEquals(
            LogicRunResponse.Submitted,
            moveTo(runId, nestedChildPath, "main.steps/Gate.branches/Branch.steps/G1", frameFor(nestedChildPath)))
        awaitState(LogicRunState.Paused)
        assertFrameNextToRun(nestedChildPath, "main.steps/Gate.branches/Branch.steps/G1")
        assertFrameNextToRun(nestedPath, "main.steps/Call")
        assertEquals(4, CountingStep.count.get())

        resume(runId)
        awaitDone()

        // G1 re-ran (5) and C3 followed (6); C1 / C2 were adopted, and the parent only added After (7).
        assertEquals(7, CountingStep.count.get())
        assertEquals("2", stepDisplay(runId, nestedChildPath, "main.steps/C1"), "C1 was adopted, not re-run")
        assertEquals("1", stepDisplay(runId, nestedPath, "main.steps/Before"), "the parent did not move")
    }


    @Test
    fun middleFrameJumpAbandonsTheDeeperInvocation() {
        val runId = startPaused(chainPath)       // before A1
        step(runId)                              // A1, before CallB
        step(runId)                              // into B, before B1
        step(runId)                              // B1, before CallC
        step(runId)                              // into C, before C1
        step(runId)                              // C1, before C2
        assertEquals(3, CountingStep.count.get(), "A1, B1, C1")

        // Addressed to the MIDDLE frame while a deeper one is live: B's jump drops CallC's outcome, so C's
        // in-flight invocation is abandoned and its frame goes with the rebuild.
        assertEquals(
            LogicRunResponse.Submitted,
            moveTo(runId, chainBPath, "main.steps/B1", frameFor(chainBPath)))
        awaitState(LogicRunState.Paused)
        assertFrameNextToRun(chainBPath, "main.steps/B1")
        // The root is this jump's only transit hop — B is the frame it addresses — so CallB is the one
        // suppressed boundary whose position has to be re-established.
        assertFrameNextToRun(chainPath, "main.steps/CallB")
        assertNull(deepestFrameOrNull(chainCPath), "the abandoned invocation's frame is gone")
        assertEquals(3, CountingStep.count.get())

        resume(runId)
        awaitDone()

        // B1 (4), then CallC re-hosts C, which must EXECUTE C1 (5) and C2 (6) rather than replay-adopt the
        // abandoned invocation's capture — adoption would short-circuit C1 and stop the total at 7.
        assertEquals(
            8, CountingStep.count.get(),
            "the re-hosted sub-Script executed its steps instead of adopting the abandoned capture")
        assertEquals("1", stepDisplay(runId, chainPath, "main.steps/A1"), "the root frame did not move")
    }


    @Test
    fun deepestFrameJumpReEstablishesEveryTransitFramePosition() {
        val runId = startPaused(chainPath)       // before A1
        step(runId)                              // A1, before CallB
        step(runId)                              // into B, before B1
        step(runId)                              // B1, before CallC
        step(runId)                              // into C, before C1
        step(runId)                              // C1, before C2
        assertEquals(3, CountingStep.count.get(), "A1, B1, C1")

        // Addressed to the DEEPEST frame, so the call-site path carries two hops and BOTH are claimed on the
        // rebuild — the only shape in which a transit frame hosts another transit frame.
        assertEquals(
            LogicRunResponse.Submitted,
            moveTo(runId, chainCPath, "main.steps/C1", frameFor(chainCPath)))
        awaitState(LogicRunState.Paused)
        assertFrameNextToRun(chainCPath, "main.steps/C1")
        assertFrameNextToRun(chainBPath, "main.steps/CallC")
        assertFrameNextToRun(chainPath, "main.steps/CallB")
        assertEquals(3, CountingStep.count.get(), "the jump itself runs nothing")

        resume(runId)
        awaitDone()

        // Only C re-ran (C1, C2 -> 4, 5); both transit frames adopted their completed steps and merely carried
        // on afterwards (B2 -> 6, A2 -> 7).
        assertEquals(7, CountingStep.count.get())
        assertEquals("1", stepDisplay(runId, chainPath, "main.steps/A1"), "the root frame did not move")
        assertEquals("2", stepDisplay(runId, chainBPath, "main.steps/B1"), "the middle frame did not move")
    }


    @Test
    fun deepFrameOfASelfHostingScriptIsAddressable() {
        val runId = startPaused(recursivePath)   // before R1
        step(runId)                              // R1, before Next
        step(runId)                              // Next, before More
        step(runId)                              // More, before Gate
        step(runId)                              // into Gate (More true), before SelfCall
        step(runId)                              // into the nested frame, before its R1
        step(runId)                              // the nested R1, before its Next
        step(runId)                              // its Next, before its More
        step(runId)                              // its More, before its Gate
        step(runId)                              // through Gate (More false), before its R2

        // Two live frames of ONE self-hosting document: same stable id, same ObjectLocation, told apart on the
        // wire only by execution id.
        val rootFrame = context.serverLogicController.status().active?.frame
            ?: fail("Run is not active")
        val deepFrame = deepestFrame(recursivePath)
        assertEquals(rootFrame.objectLocation, deepFrame.objectLocation, "one document, two invocations")
        assertNotEquals(rootFrame.executionId, deepFrame.executionId, "each invocation is separately addressable")

        // What this pins is that such a frame can be ADDRESSED at all: framePathTo recurses past a frame whose
        // stable id equals the root's, and every gate clears a recursive path — notably canDescendThrough on a
        // SelfCall RunStep nested inside an If branch. The target is deliberately NOT where the deep frame is
        // parked, so the request reaches those gates instead of short-circuiting on the already-at-target no-op.
        //
        // It pins NOTHING about which frame then moves, nor about the resulting counts. The migration capture
        // register is keyed by stable id alone, so two simultaneously live frames of one document collide on a
        // single key with no defined winner (logic-spec §5, the "Undefined" note); asserting an outcome there
        // would turn undefined behaviour into a contract.
        assertEquals(
            LogicRunResponse.Submitted,
            moveTo(runId, recursivePath, "main.steps/R1", deepFrame.executionId))
        awaitState(LogicRunState.Paused)
    }


    @Test
    fun targetInADocumentWithNoLiveFrameIsRejected() {
        val runId = startPaused(nestedPath)      // before Before; Call has not hosted the child yet

        // A null execution id addresses the ROOT frame, whose Script owns no element of the child document.
        assertEquals(
            LogicRunResponse.Rejected,
            moveTo(runId, nestedChildPath, "main.steps/C1", null))
        assertFrameNextToRun(nestedPath, "main.steps/Before")
        assertEquals(0, CountingStep.count.get(), "the run is untouched")
    }


    @Test
    fun jumpAddressedToASettledFrameIsRejected() {
        val runId = startPaused(nestedPath)      // before Before
        step(runId)                              // Before, before Call
        step(runId)                              // into the child, before C1

        // The live frame tree prunes a terminal child, but the engine snapshot keeps it — so a client poll that
        // raced the frame's settle can still name it, and the liveness gate is what refuses it.
        val settlingFrame = frameFor(nestedChildPath)
        stepOut(runId)                           // the child runs to completion, back in the parent before After
        assertEquals(5, CountingStep.count.get(), "Before, C1, C2, G1, C3")

        assertEquals(
            LogicRunResponse.Rejected,
            moveTo(runId, nestedChildPath, "main.steps/C1", settlingFrame))
        assertFrameNextToRun(nestedPath, "main.steps/After")
        assertEquals(5, CountingStep.count.get(), "the run is untouched")
    }


    @Test
    fun loopHostedTransitHopDescendsIntoTheResumedIteration() {
        val runId = startPaused(loopTransitPath)
        // Park right after the 3rd Count: iteration 0's child invocation completed (counts 1-2) and iteration
        // 1's is mid-flight at C2 — so the loop and the child it hosts are both in flight.
        stepUntilCount(runId, 3)

        // The root frame descends through a call-site INSIDE its ForEach body. The loop re-enters at its
        // carried cursor (item 2), its completed body prefix replay-adopts, and the one-shot descend claim
        // rides exactly that resumed iteration.
        assertEquals(
            LogicRunResponse.Submitted,
            moveTo(runId, loopTransitChildPath, "main.steps/C1", frameFor(loopTransitChildPath)))
        awaitState(LogicRunState.Paused)
        assertFrameNextToRun(loopTransitChildPath, "main.steps/C1")
        assertFrameNextToRun(loopTransitPath, "main.steps/Loop.steps/Call")
        assertEquals(3, CountingStep.count.get(), "the jump itself runs nothing")

        resume(runId)
        awaitDone()

        // The repositioned invocation re-ran C1 + C2 (4, 5), iteration 2's fresh invocation ran its own
        // (6, 7), and After closed the run (8). A loop restarted at iteration 0 would have re-hosted items 1
        // and 2 as well, reaching 10.
        assertEquals(
            8, CountingStep.count.get(),
            "only the repositioned iteration re-ran — the loop did not restart at iteration 0")
        assertEquals(
            "60", stepDisplay(runId, loopTransitPath, "main.steps/Total"),
            "each iteration kept its own item: [10, 20, 30]")
    }


    @Test
    fun doWhileHostedTransitHopDescendsIntoTheResumedIteration() {
        val runId = startPaused(doWhileTransitPath)
        stepUntilCount(runId, 2)                 // iteration 2's Tick, before Call
        step(runId)                              // into the child, before D1
        step(runId)                              // D1, before D2

        // The other loop archetype, whose carry is a plain completed-iteration count rather than a cursor: the
        // resume rule is the same, so Tick — this iteration's completed body prefix — replay-adopts.
        assertEquals(
            LogicRunResponse.Submitted,
            moveTo(runId, doWhileTransitChildPath, "main.steps/D1", frameFor(doWhileTransitChildPath)))
        awaitState(LogicRunState.Paused)
        assertFrameNextToRun(doWhileTransitChildPath, "main.steps/D1")
        assertFrameNextToRun(doWhileTransitPath, "main.steps/Loop.steps/Call")
        assertEquals(2, CountingStep.count.get(), "the jump itself runs nothing")
        assertEquals(
            "2", stepDisplay(runId, doWhileTransitPath, "main.steps/Loop.steps/Tick"),
            "the in-flight iteration's Tick was adopted; a loop re-entered without its carry would have reset " +
                "it, re-run it, and parked there short of the call-site")

        resume(runId)
        awaitDone()

        assertEquals(
            4, CountingStep.count.get(),
            "the resumed iteration finished without re-running Tick, one more iteration ran (3), the " +
                "condition ended the loop, and After closed the run (4)")
    }


    @Test
    fun nestedLoopTransitHopDescendsThroughBothLoops() {
        val runId = startPaused(nestedLoopTransitPath)
        // Park right after the 7th Count: the LAST child invocation (outer item 2, inner item 2) is mid-flight
        // at C2, so both loops are in flight and both must re-enter at their own cursor.
        stepUntilCount(runId, 7)

        // One frame, three descend entries claimed on a single rebuild: Outer, Inner, and the call-site.
        assertEquals(
            LogicRunResponse.Submitted,
            moveTo(runId, loopTransitChildPath, "main.steps/C1", frameFor(loopTransitChildPath)))
        awaitState(LogicRunState.Paused)
        assertFrameNextToRun(loopTransitChildPath, "main.steps/C1")
        assertFrameNextToRun(nestedLoopTransitPath, "main.steps/Outer.steps/Inner.steps/Call")
        assertEquals(7, CountingStep.count.get(), "the jump itself runs nothing")

        resume(runId)
        awaitDone()

        // Only the repositioned invocation re-ran (C1, C2 -> 8, 9) before After closed the run (10). An inner
        // loop that replayed from its first item would have re-hosted one more invocation, reaching 12.
        assertEquals(
            10, CountingStep.count.get(),
            "both loops re-entered at their carried cursors")
        assertEquals(
            "60", stepDisplay(runId, nestedLoopTransitPath, "main.steps/Total"),
            "every invocation kept its own inner item: [[10, 20], [10, 20]]")
    }


    @Test
    fun nonRepositionableTransitHopIsRejected() {
        val runId = startPaused(transitPath)     // before Before
        step(runId)                              // Before, before Seed
        step(runId)                              // Seed, before Call
        step(runId)                              // into the Flow, before FmtInput
        step(runId)                              // FmtInput, before FmtCall
        step(runId)                              // into the deepest Script, before Deep
        assertEquals(1, CountingStep.count.get(), "Before")

        // Two independent gates would refuse this path: a Flow is not Repositionable, and FlowRun hosts without
        // a callerStableId so the hop below it is unaddressable. The Repositionable check runs first — it is
        // asked of the Flow frame itself, before that frame is asked to carry a call-site.
        assertEquals(
            LogicRunResponse.Rejected,
            moveTo(runId, transitChildPath, "main.steps/Deeper", frameFor(transitChildPath)))
        assertFrameNextToRun(transitChildPath, "main.steps/Deep")
        assertEquals(1, CountingStep.count.get(), "the run is untouched")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val snapshot: GraphDefinitionAttempt
        get() = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())


    private fun startPaused(documentPath: DocumentPath): LogicRunId {
        val controller = context.serverLogicController
        val main = ObjectLocation(documentPath, ObjectPath.parse("main"))
        val runId = controller.startAttempt(main, snapshot, false).runIdOrNull
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


    // Step one boundary at a time until [CountingStep] has run [target] times: the park right after the Nth
    // Count is at the next boundary of that same invocation, which is what leaves both the hosting loop and
    // the child it hosts mid-flight. Boundary counts differ per fixture, hence a target rather than a
    // hand-counted step list.
    private fun stepUntilCount(runId: LogicRunId, target: Int) {
        var guard = 0
        while (CountingStep.count.get() < target && guard < 200) {
            step(runId)
            guard += 1
        }
        assertEquals(target, CountingStep.count.get(), "expected to park right after Count invocation $target")
    }


    private fun stepOut(runId: LogicRunId) {
        context.serverLogicController.stepOut(runId, snapshot)
        awaitState(LogicRunState.Paused)
    }


    private fun resume(runId: LogicRunId) {
        context.serverLogicController.continueOrStart(runId, snapshot)
    }


    private fun moveTo(
        runId: LogicRunId,
        documentPath: DocumentPath,
        objectPath: String,
        executionId: LogicExecutionId?
    ): LogicRunResponse {
        return context.serverLogicController.moveTo(
            runId, ObjectLocation(documentPath, ObjectPath.parse(objectPath)), snapshot, executionId)
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


    private fun stepDisplay(runId: LogicRunId, documentPath: DocumentPath, objectPath: String): Any? {
        return stepTrace(runId, documentPath, objectPath)?.displayValue?.get()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The live frame of [documentPath] a move-to addresses: the DEEPEST match, which is the invocation the user
    // is stepping inside — and the only reading that distinguishes anything under self-recursion, where one
    // document is live in several frames at once.
    private fun deepestFrameOrNull(documentPath: DocumentPath): LogicRunFrameInfo? {
        val root = context.serverLogicController.status().active?.frame
            ?: return null

        var deepest: Pair<Int, LogicRunFrameInfo>? = null
        fun visit(frame: LogicRunFrameInfo, depth: Int) {
            val current = deepest
            if (frame.objectLocation.documentPath == documentPath && (current == null || depth > current.first)) {
                deepest = depth to frame
            }
            frame.dependencies.forEach { visit(it, depth + 1) }
        }
        visit(root, 0)

        return deepest?.second
    }


    private fun deepestFrame(documentPath: DocumentPath): LogicRunFrameInfo {
        return deepestFrameOrNull(documentPath)
            ?: fail("No live frame for $documentPath")
    }


    private fun frameFor(documentPath: DocumentPath): LogicExecutionId {
        return deepestFrame(documentPath).executionId
    }


    private fun assertFrameNextToRun(documentPath: DocumentPath, objectPath: String) {
        assertEquals(
            ObjectLocation(documentPath, ObjectPath.parse(objectPath)),
            deepestFrame(documentPath).position,
            "next to run in $documentPath")
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
