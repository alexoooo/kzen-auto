package tech.kzen.auto.server.service.impl

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.run.model.LogicRunFrameInfo
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * Integration coverage for the LINKED-document live edit (script-improvements phase 3): editing a hosted
 * callee document (a RunStep's sub-script) while the CALLER's run is paused triggers migration, exactly like
 * editing the caller itself. The `instructions` link is weak (`by: Nominal`), so the callee is outside the
 * root's transitive definition closure — [LinkedLogicDocuments] widens the change signal to cover it.
 *
 * Uses the `run` fixture pair (parent: Seed=6 -> Call{RunStep -> child} -> Result; child computes
 * `number + 1`, so the unedited chain yields 7). At the pause Seed has completed but Call has not run; the
 * edit changes the CHILD's formula to `number + 100`. Only a detected callee edit yields 106: the rebuilt run
 * compiles the callee fresh from the post-edit notation, while a silently-ignored edit would host the
 * stale callee compiled from the run-start notation snapshot and yield 7.
 *
 * The second test runs the same pair the other way round — the callee has already COMPLETED when the CALLER is
 * edited. The rebuilt spine replay-adopts the completed RunStep instead of re-invoking it, so nothing
 * re-creates the sub-Script's frame; the engine has to carry the settled frame across the barrier itself
 * (logic-spec §5 "settled frames survive the rebuild"), or every trace query — all of which project the node
 * tree — reports the finished sub-document as having no execution state at all.
 *
 * The third test covers the remaining case, the RunStep MID-FLIGHT: the run is parked inside the sub-Script's
 * own frame when the edit lands. It records that the edit-migrate pops the position out to the caller's
 * RunStep and leaves no live child frame — the paused rebuild replays the caller's completed prefix, parks at
 * the mid-flight RunStep's own boundary (it holds no outcome to replay-adopt), and so never re-hosts the
 * callee; the callee's mid-flight capture goes unclaimed and is swept as an orphan.
 */
class ServerLogicControllerLinkedDocumentMigrationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val parentPath = DocumentPath.parse("test/script/engine/script-engine-run-test.yaml")
    private val childPath = DocumentPath.parse("test/script/engine/script-engine-child-test.yaml")

    private val scriptLocation = ObjectLocation(parentPath, ObjectPath.parse("main"))
    private val childScriptLocation = ObjectLocation(childPath, ObjectPath.parse("main"))
    private val seedLocation = ObjectLocation(parentPath, ObjectPath.parse("main.steps/Seed"))
    private val callLocation = ObjectLocation(parentPath, ObjectPath.parse("main.steps/Call"))
    private val resultLocation = ObjectLocation(parentPath, ObjectPath.parse("main.steps/Result"))
    private val childPlusLocation = ObjectLocation(childPath, ObjectPath.parse("main.steps/Plus"))

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
    fun editingHostedCalleeWhileCallerPausedMigratesTheCaller() {
        val controller = context.serverLogicController

        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation)
        val edited = AutoTestUtils.graphDefinitionAttempt(
            edit(baseNotation, childPlusLocation, "code", "number + 100"))

        val runId = controller.start(scriptLocation, base)
            ?: fail("Unable to start run")

        // Step until Seed (the first step) has completed but the hosting Call step has not yet run — the
        // callee compiles lazily on Call's first host(), so the edit must land before that compile.
        var guard = 0
        while (!isDone(runId, seedLocation) && guard < 50) {
            controller.step(runId, base)
            awaitState(LogicRunState.Paused)
            guard += 1
        }
        assertTrue(isDone(runId, seedLocation), "Seed should complete before the edit")
        assertFalse(isDone(runId, callLocation), "Call must not have run before the edit")

        // The edit was fabricated OUT-OF-BAND (NotationReducer on a local notation copy), so hand the
        // controller the store notification production would have delivered (edit detection is event-driven).
        runBlocking { controller.onStoreRefresh(edited) }

        // Resume against the edited snapshot: only the WIDENED signal sees the callee-document change (the
        // parent document's own closure is untouched), recompiles, and migrates.
        controller.continueOrStart(runId, edited)
        awaitDone()

        assertEquals(
            "106", resultDisplay(runId),
            "the callee edit (number + 1 -> number + 100) must take effect on the paused caller's next " +
                "host() — a signal blind to the weakly-linked callee would resume the stale callee -> 7")
    }


    @Test
    fun aCompletedRunStepsSubDocumentKeepsItsExecutionStateAcrossAnEdit() {
        val controller = context.serverLogicController

        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation)

        val runId = controller.start(scriptLocation, base)
            ?: fail("Unable to start run")

        // Step until the hosting Call step has COMPLETED — its sub-Script frame is settled but retained, which
        // is what makes the sub-document navigable after the fact.
        var guard = 0
        while (!isDone(runId, callLocation) && guard < 50) {
            controller.step(runId, base)
            awaitState(LogicRunState.Paused)
            guard += 1
        }
        assertTrue(isDone(runId, callLocation), "the RunStep should complete before the edit")
        assertNotNull(
            context.logicTrace.mostRecent(childScriptLocation),
            "before the edit the finished sub-document resolves to its own execution")

        // An edit to the not-yet-run Result: it triggers the rebuild without touching anything the completed
        // prefix reports. The rebuilt spine replay-adopts Call rather than re-invoking it, so ONLY the engine's
        // carry of settled frames can keep the sub-Script's execution addressable.
        val edited = AutoTestUtils.graphDefinitionAttempt(
            edit(baseNotation, resultLocation, "code", "Call + 0"))
        runBlocking { controller.onStoreRefresh(edited) }

        controller.step(runId, edited)
        awaitState(LogicRunState.Paused)

        val childExecution = context.logicTrace.mostRecent(childScriptLocation)
            ?: fail("the finished sub-document must still resolve to an execution after the caller was edited")

        // This is the exact read ScriptProgressStore performs when the user navigates into the sub-document:
        // a null execution id above blanks its whole progress view, and an empty snapshot here blanks its steps.
        val childSnapshot = context.logicTrace.lookup(childExecution, LogicTraceQuery(LogicTracePath.root))
            ?: fail("the carried frame must still serve its own trace snapshot")
        val plusStableId = context.objectStableMapper.objectStableId(childPlusLocation)
        val plusEntry = childSnapshot.values[LogicTracePath.ofObjectStableId(plusStableId)]
            ?: fail("the sub-document's step must keep its trace across the caller's edit")
        assertEquals(
            StepTrace.State.Done, StepTrace.ofExecutionValue(plusEntry.value).state,
            "the sub-document's step is still reported done, exactly as it was before the edit")

        // The RunStep's screenshot film strip scopes itself by walking the execution tree from the viewed
        // document's execution, so the carried frame has to keep its parent link and its call-site too.
        val callStableId = context.objectStableMapper.objectStableId(callLocation)
        val rootExecutionId = controller.status().active?.frame?.executionId
            ?: fail("the run must still be active")
        val childExecutions = context.logicTrace.lookupRunExecutions(runId)
            .filter { it.parentExecutionId == rootExecutionId }
        assertEquals(
            listOf(callStableId), childExecutions.map { it.callerStableId },
            "the carried frame stays a child of the root execution, attributed to the RunStep that hosted it")
    }


    @Test
    fun editingWhileParkedInsideTheSubScriptPopsThePositionOutToTheRunStep() {
        val controller = context.serverLogicController

        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation)

        val runId = controller.start(scriptLocation, base)
            ?: fail("Unable to start run")

        // Stepping is StepMode.Into, so it descends: a live child frame under the root means the run is parked
        // inside the sub-Script, with the hosting RunStep mid-flight — neither completed nor replayable.
        var guard = 0
        while (liveChildFrames().isEmpty() && guard < 50) {
            controller.step(runId, base)
            awaitState(LogicRunState.Paused)
            guard += 1
        }
        assertEquals(
            listOf(childScriptLocation), liveChildFrames().map { it.objectLocation },
            "the run should be parked inside the sub-Script's own frame before the edit")
        assertFalse(isDone(runId, callLocation), "the hosting RunStep must still be mid-flight")

        val edited = AutoTestUtils.graphDefinitionAttempt(
            edit(baseNotation, childPlusLocation, "code", "number + 100"))
        runBlocking { controller.onStoreRefresh(edited) }

        controller.step(runId, edited)
        awaitState(LogicRunState.Paused)

        val rootFrame = controller.status().active?.frame
            ?: fail("the run must still be active after the edit")
        assertEquals(
            callLocation, rootFrame.position,
            "the paused rebuild replays the completed prefix and parks at the mid-flight RunStep itself, " +
                "so the position pops out of the sub-Script up to its caller")
        assertTrue(
            rootFrame.dependencies.isEmpty(),
            "parking at the RunStep's own boundary happens BEFORE it hosts, so the rebuilt run has no live " +
                "sub-Script frame — the mid-flight callee invocation is abandoned by the edit")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun liveChildFrames(): List<LogicRunFrameInfo> {
        return context.serverLogicController.status().active?.frame?.dependencies
            ?: listOf()
    }


    private fun edit(
        notation: GraphNotation,
        location: ObjectLocation,
        attribute: String,
        value: String
    ): GraphNotation {
        return NotationReducer()
            .applyStructural(
                notation,
                UpsertAttributeCommand(
                    location, AttributeName(attribute), ScalarAttributeNotation(value)))
            .graphNotation
    }


    private fun isDone(runId: LogicRunId, location: ObjectLocation): Boolean {
        val trace = stepTrace(runId, location)
            ?: return false
        return trace.state == StepTrace.State.Done
    }


    private fun resultDisplay(runId: LogicRunId): Any? {
        return stepTrace(runId, resultLocation)?.displayValue?.get()
    }


    private fun stepTrace(runId: LogicRunId, location: ObjectLocation): StepTrace? {
        val snapshot = context.logicTrace.lookupRun(runId, LogicTraceQuery(LogicTracePath.root))
            ?: return null
        val stableId = context.objectStableMapper.objectStableId(location)
        val entry = snapshot.values[LogicTracePath.ofObjectStableId(stableId)]
            ?: return null
        return StepTrace.ofExecutionValue(entry.value)
    }


    @Suppress("SameParameterValue")
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
