package tech.kzen.auto.server.service.impl

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.StructuralNotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * Deleting a step from a document a run is PAUSED in — the live-edit case where the edited element is gone
 * rather than changed (logic-spec §5 "an element the edit removed is disposed").
 *
 * Three things break differently here from an attribute edit, and each has a test below: the run's position
 * names an element the deletion unmapped, so the status projection has to tolerate an unresolvable stable id;
 * a stable id is the element's ADDRESS, so a step created where the deleted one stood mints the same id and
 * must not inherit its outcome; and a completed container's nested steps are only re-traced by the replay
 * adopt, so their display must survive the rebuild.
 *
 * The edits are fabricated out-of-band ([NotationReducer] over a local notation copy) rather than applied to
 * the graph store, which would rewrite the fixture on disk — so the notification the store would have
 * delivered is handed to both observers by [applyEdit].
 */
class ServerLogicControllerStepRemovalTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/script-engine-step-removal-test.yaml")
    private val scriptLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
    private val firstLocation = ObjectLocation(documentPath, ObjectPath.parse("main.steps/First"))
    private val secondLocation = ObjectLocation(documentPath, ObjectPath.parse("main.steps/Second"))
    private val thirdLocation = ObjectLocation(documentPath, ObjectPath.parse("main.steps/Third"))

    private val ifDocumentPath = DocumentPath.parse("test/script-engine-if-test.yaml")
    private val ifScriptLocation = ObjectLocation(ifDocumentPath, ObjectPath.parse("main"))
    private val branchLocation = ObjectLocation(ifDocumentPath, ObjectPath.parse("main.steps/Branch"))
    private val yesLocation = ObjectLocation(
        ifDocumentPath, ObjectPath.parse("main.steps/Branch.branches/Branch.steps/Yes"))
    private val ifResultLocation = ObjectLocation(ifDocumentPath, ObjectPath.parse("main.steps/Result"))

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
    fun deletingTheNextToRunStepLeavesTheStatusReadableAndTheRunSteppable() {
        val controller = context.serverLogicController

        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation)

        val runId = controller.start(scriptLocation, base)
            ?: fail("Unable to start run")

        // Two steps: park at First, then run it and park at Second — the step the user is about to delete.
        controller.step(runId, base)
        awaitState(LogicRunState.Paused)
        controller.step(runId, base)
        awaitState(LogicRunState.Paused)
        assertEquals(secondLocation, position(), "the run must be parked at the step being deleted")

        val edited = applyEdit(baseNotation, RemoveObjectCommand(secondLocation))

        // The status is what BOTH the poll and the SSE stream serialize, so a throw here takes out the client's
        // only view of the run — including the step that would migrate it past the deletion.
        assertNull(position(), "a deleted next-to-run step reports no position rather than failing the status")

        controller.step(runId, edited)
        awaitState(LogicRunState.Paused)
        assertEquals(thirdLocation, position(), "the rebuilt run re-parks at the step after the deleted one")
    }


    @Test
    fun aStepCreatedWhereADeletedOneStoodRunsInsteadOfAdoptingItsOutcome() {
        val controller = context.serverLogicController

        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation)
        val secondIndex = baseNotation.documents[documentPath]!!.indexOf(secondLocation.objectPath)

        val runId = controller.start(scriptLocation, base)
            ?: fail("Unable to start run")

        var guard = 0
        while (!isDone(runId, secondLocation) && guard < 50) {
            controller.step(runId, base)
            awaitState(LogicRunState.Paused)
            guard += 1
        }
        assertEquals("2", display(runId, secondLocation), "Second should complete before the edit")
        val secondStableId = context.objectStableMapper.objectStableId(secondLocation)

        // Replace the completed step with a different one at the same address, as a user does when they delete
        // a step and add its replacement. Both mint the same stable id.
        val edited = applyEdit(
            baseNotation,
            RemoveObjectCommand(secondLocation),
            AddObjectCommand(
                secondLocation,
                PositionRelation.at(secondIndex.value),
                ObjectNotation.ofParent(ObjectName("FormulaStep"))),
            UpsertAttributeCommand(secondLocation, AttributeName("code"), ScalarAttributeNotation("22")))

        // Minting is get-or-create, so anything that addresses the replacement before the rebuild — a
        // breakpoint push, a trace lookup — restores a mapping identical to the deleted step's. "The id still
        // resolves" is therefore not evidence of continuity; only the removal report is.
        assertEquals(
            secondStableId, context.objectStableMapper.objectStableId(secondLocation),
            "the replacement mints the deleted step's id")

        controller.step(runId, edited)
        awaitState(LogicRunState.Paused)
        assertEquals(
            secondLocation, position(),
            "the replacement is not carried work, so the rebuilt run parks at it rather than replaying past it")

        controller.step(runId, edited)
        awaitState(LogicRunState.Paused)
        assertEquals(
            "22", display(runId, secondLocation),
            "the replacement produced its OWN value — inheriting the deleted step's id must not report it done")
    }


    @Test
    fun completedNestedStepsKeepTheirTraceAcrossAMigration() {
        val controller = context.serverLogicController

        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation)

        val runId = controller.start(ifScriptLocation, base)
            ?: fail("Unable to start run")

        var guard = 0
        while (!isDone(runId, branchLocation) && guard < 50) {
            controller.step(runId, base)
            awaitState(LogicRunState.Paused)
            guard += 1
        }
        assertTrue(isDone(runId, branchLocation), "the If container should complete before the edit")
        assertTrue(isDone(runId, yesLocation), "its taken branch's step completes with it")

        // Any edit to the closure triggers the rebuild; the not-yet-run Result is the one step whose change
        // can't itself disturb what the completed prefix reports.
        val edited = applyEdit(
            baseNotation,
            UpsertAttributeCommand(ifResultLocation, AttributeName("code"), ScalarAttributeNotation("Branch + 1")))

        controller.step(runId, edited)
        awaitState(LogicRunState.Paused)

        assertTrue(
            isDone(runId, branchLocation),
            "the completed container re-adopts its outcome, so it still reports done")
        assertTrue(
            isDone(runId, yesLocation),
            "adopting the container adopts its branch's completed steps too — nothing else re-walks them")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The notification the graph store would have published for each command, delivered to the two observers a
    // live run depends on: the stable mapper (which unmaps a removed element) and the controller (whose
    // edit-detection is event-driven, so a release only reconciles once some notation event has landed).
    private fun applyEdit(
        notation: GraphNotation,
        vararg commands: StructuralNotationCommand
    ) = run {
        val reducer = NotationReducer()
        var current = notation
        val events = mutableListOf<NotationEvent>()
        for (command in commands) {
            val transition = reducer.applyStructural(current, command)
            current = transition.graphNotation
            events.add(transition.notationEvent)
        }

        val edited = AutoTestUtils.graphDefinitionAttempt(current)
        events.forEach { context.objectStableMapper.apply(it) }
        runBlocking { context.serverLogicController.onStoreRefresh(edited) }
        edited
    }


    private fun position(): ObjectLocation? {
        return context.serverLogicController.status().active?.frame?.position
    }


    private fun isDone(runId: LogicRunId, location: ObjectLocation): Boolean {
        return stepTrace(runId, location)?.state == StepTrace.State.Done
    }


    private fun display(runId: LogicRunId, location: ObjectLocation): Any? {
        return stepTrace(runId, location)?.displayValue?.get()
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
}
