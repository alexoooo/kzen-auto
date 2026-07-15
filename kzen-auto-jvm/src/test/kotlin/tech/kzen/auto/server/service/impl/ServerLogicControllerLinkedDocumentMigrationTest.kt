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
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 */
class ServerLogicControllerLinkedDocumentMigrationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val parentPath = DocumentPath.parse("test/script-engine-run-test.yaml")
    private val childPath = DocumentPath.parse("test/script-engine-child-test.yaml")

    private val scriptLocation = ObjectLocation(parentPath, ObjectPath.parse("main"))
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
        while (! isDone(runId, seedLocation) && guard < 50) {
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


    //-----------------------------------------------------------------------------------------------------------------
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
