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
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
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
 * Integration coverage for [ServerLogicController]'s live-edit migration driving the SCRIPT flavour (logic-spec
 * §5): pause -> edit -> resume through the PUBLIC control surface (start / step / continueOrStart), proving the
 * notation-compile path carries completed work across an edit. Complements the engine-level
 * [tech.kzen.auto.server.exec.script.ScriptMigrationTest] (mechanism) and the Job-flavour controller coverage in
 * [ServerLogicControllerMigrationTest] (the generic edit-detection): here the new thing proven is that
 * [tech.kzen.auto.server.exec.script.ScriptLogicCompiler] recompiles to a logic whose step stable ids MATCH the
 * original, so the Script root's captured outcomes line up on the rebuilt run.
 *
 * Uses the `if` fixture (Flag -> Branch{ first branch: Yes : else/No } -> Result). At the pause Flag has
 * completed; the edit changes BOTH a completed step (Flag true -> false) and a not-yet-run step (Yes 10 -> 99).
 * Only a correct replay yields 99: Flag is re-adopted as `true` (so Branch still takes the FIRST branch — a clean
 * restart would re-run Flag as `false` -> else -> 20) while the live `Yes` edit takes effect (proving migration
 * actually fired against the new definition — a silently-ignored edit would resume the old `Yes` -> 10).
 */
class ServerLogicControllerScriptMigrationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/script-engine-if-test.yaml")
    private val scriptLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
    private val flagLocation = ObjectLocation(documentPath, ObjectPath.parse("main.steps/Flag"))
    private val yesLocation = ObjectLocation(documentPath, ObjectPath.parse("main.steps/Branch.branches/Branch.steps/Yes"))
    private val resultLocation = ObjectLocation(documentPath, ObjectPath.parse("main.steps/Result"))

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
    fun editingScriptWhilePausedResumesFromCompletedPrefixThroughController() {
        val controller = context.serverLogicController

        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation)
        val edited = AutoTestUtils.graphDefinitionAttempt(
            edit(
                baseNotation,
                Triple(flagLocation, "code", "false"),
                Triple(yesLocation, "code", "99")))

        val runId = controller.start(scriptLocation, base)
            ?: fail("Unable to start run")

        // Step until Flag (the first step) has completed but Branch / Result have not yet run — the completed
        // prefix the resume must preserve. Each step passes the base snapshot, so none of them migrate.
        var guard = 0
        while (! isDone(runId, flagLocation) && guard < 50) {
            controller.step(runId, base)
            awaitState(LogicRunState.Paused)
            guard += 1
        }
        assertTrue(isDone(runId, flagLocation), "Flag should complete before the edit")
        assertFalse(isDone(runId, resultLocation), "Result must not have run before the edit")

        // The edit was fabricated OUT-OF-BAND (NotationReducer on a local notation copy — the graph store never
        // saw a command), so hand the controller the store notification production would have delivered: edit
        // detection is event-driven (the controller observes the graph store), and a release only reconciles
        // against the baseline once some notation event has landed.
        runBlocking { controller.onStoreRefresh(edited) }

        // Resume against the edited snapshot: the controller detects the change, recompiles, and migrates.
        controller.continueOrStart(runId, edited)
        awaitDone()

        assertEquals(
            "99", resultDisplay(runId),
            "completed Flag re-adopted as true (THEN branch kept — not re-run as false -> else -> 20), while the " +
                "live then/Yes edit took effect (migration fired against the new definition — not resumed at 10)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun edit(
        notation: GraphNotation,
        vararg edits: Triple<ObjectLocation, String, String>
    ): GraphNotation {
        val reducer = NotationReducer()
        var current = notation
        for ((location, attribute, value) in edits) {
            current = reducer
                .applyStructural(
                    current,
                    UpsertAttributeCommand(
                        location, AttributeName(attribute), ScalarAttributeNotation(value)))
                .graphNotation
        }
        return current
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
