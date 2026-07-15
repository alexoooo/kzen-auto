package tech.kzen.auto.server.service.impl

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.script.test.CountingStep
import tech.kzen.auto.server.exec.script.test.ScriptStepTestModule
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
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * Integration coverage for MID-LOOP migration resume (loop cursors) through [ServerLogicController]'s public
 * control surface: pause mid-iteration -> edit -> resume continues at the loop's current iteration instead of
 * restarting from iteration 0 — no completed iteration's side effects re-run. The observable side effect is
 * [CountingStep], a process-global invocation counter inside each loop's body; the fixtures are shaped so the
 * final Result also discriminates resume from restart (see the fixture comments).
 *
 * Covers: ForEach resume ([forEachResumesAtCurrentIterationAcrossEdit]), DoWhile resume
 * ([doWhileResumesAtCurrentIterationAcrossEdit]), a loop whose body HOSTS a sub-script — pause inside the
 * child, resume with the in-flight invocation adopting its own capture while fresh invocations start clean
 * ([forEachHostedChildResumesAcrossMidChildEdit], the FizzBuzz Script Loop regression), resume over a
 * sequence-backed (non-Collection) Iterable via the cursor's carried live iterator
 * ([sequenceBackedItemsResumeViaCarriedLiveIterator]), a loop that completed pre-edit staying wholesale
 * short-circuited ([completedLoopShortCircuitsWholesaleAcrossEdit]), and a SECOND edit mid-loop (the cursor is
 * re-recorded on the rebuilt run's first pass, so repeated migrations compose —
 * [secondEditMidLoopStillResumes]).
 *
 * Complements [ServerLogicControllerScriptMigrationTest] (completed-prefix replay via the same control surface);
 * driving pattern (out-of-band edit + [ServerLogicController.onStoreRefresh]) is shared. Relies on the suite's
 * sequential execution for the process-global counter, like the other static-fixture tests.
 */
class ServerLogicControllerLoopMigrationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val forEachDocumentPath = DocumentPath.parse("test/script-loop-migration-test.yaml")
    private val childLoopDocumentPath = DocumentPath.parse("test/script-loop-child-migration-test.yaml")
    private val nonCollectionDocumentPath = DocumentPath.parse("test/script-loop-migration-noncollection-test.yaml")
    private val doWhileDocumentPath = DocumentPath.parse("test/script-dowhile-migration-test.yaml")

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
        context.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun forEachResumesAtCurrentIterationAcrossEdit() {
        val controller = context.serverLogicController

        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation)
        val edited = AutoTestUtils.graphDefinitionAttempt(
            edit(baseNotation, postLocation(forEachDocumentPath), "99"))

        val runId = controller.start(scriptLocation(forEachDocumentPath), base)
            ?: fail("Unable to start run")

        // Park mid-iteration-2 of 4: Count has run 3 times (iterations 0-1 complete, iteration 2's Count done)
        // and the park is at iteration 2's Second — the loop is mid-flight.
        stepUntilCount(runId, base, 3)

        runBlocking { controller.onStoreRefresh(edited) }
        controller.continueOrStart(runId, edited)
        awaitDone()

        assertEquals(
            4, CountingStep.count.get(),
            "resume at iteration 2: iterations 0-1 and iteration 2's completed Count must NOT re-execute " +
                "(a restart would re-run them, giving 7)")
        assertEquals(
            "199", resultDisplay(runId, forEachDocumentPath),
            "4 outputs collected across the edit ([10, 20, 30, 40].sum() = 100) + the live Post edit (99)")
    }


    @Test
    fun doWhileResumesAtCurrentIterationAcrossEdit() {
        val controller = context.serverLogicController

        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation)
        val edited = AutoTestUtils.graphDefinitionAttempt(
            edit(baseNotation, postLocation(doWhileDocumentPath), "99"))

        val runId = controller.start(scriptLocation(doWhileDocumentPath), base)
            ?: fail("Unable to start run")

        stepUntilCount(runId, base, 3)

        runBlocking { controller.onStoreRefresh(edited) }
        controller.continueOrStart(runId, edited)
        awaitDone()

        assertEquals(
            4, CountingStep.count.get(),
            "resume mid-iteration: the do-while runs its remaining iteration only (condition Count < 4)")
        assertEquals("99", resultDisplay(runId, doWhileDocumentPath))
    }


    @Test
    fun forEachHostedChildResumesAcrossMidChildEdit() {
        // The FizzBuzz Script Loop shape: an IntRange loop (progressions are re-iterable, so the cursor
        // RESUMES) whose body hosts a sub-script per item. The pause lands INSIDE iteration 2's child, so the
        // migration barrier captures the mid-flight child invocation — the resume must let exactly that
        // invocation adopt its capture (Count not re-run), while iteration 3's FRESH invocation must NOT
        // adopt it (the iteration reset discards it; logic-spec §5 invocation identity).
        val controller = context.serverLogicController

        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation)
        val edited = AutoTestUtils.graphDefinitionAttempt(
            edit(baseNotation, postLocation(childLoopDocumentPath), "99"))

        val runId = controller.start(scriptLocation(childLoopDocumentPath), base)
            ?: fail("Unable to start run")

        // Park inside iteration 2's child: invocations 1-2 completed, invocation 3's Count done (its Val not).
        stepUntilCount(runId, base, 3)

        runBlocking { controller.onStoreRefresh(edited) }
        controller.continueOrStart(runId, edited)
        awaitDone()

        assertEquals(
            4, CountingStep.count.get(),
            "resume at iteration 2: completed invocations don't re-run, the in-flight child invocation " +
                "adopts its own capture (Count replayed, not re-executed), and iteration 3's fresh " +
                "invocation runs live exactly once")
        assertEquals(
            "199", resultDisplay(runId, childLoopDocumentPath),
            "item-correct child results [10, 20, 30, 40].sum() = 100 + the live Post edit (99) — a stale " +
                "capture adopted by a fresh invocation would collect another item's value")
    }


    @Test
    fun sequenceBackedItemsResumeViaCarriedLiveIterator() {
        // Sequence-wrapped items (neither Collection nor progression): the cursor carries the LIVE iterator
        // across the migration, so even a non-re-iterable Iterable resumes at its current iteration — no
        // restart, no re-run side effects — with item-correct child values.
        val controller = context.serverLogicController

        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation)
        val edited = AutoTestUtils.graphDefinitionAttempt(
            edit(baseNotation, postLocation(nonCollectionDocumentPath), "99"))

        val runId = controller.start(scriptLocation(nonCollectionDocumentPath), base)
            ?: fail("Unable to start run")

        stepUntilCount(runId, base, 3)

        runBlocking { controller.onStoreRefresh(edited) }
        controller.continueOrStart(runId, edited)
        awaitDone()

        assertEquals(
            4, CountingStep.count.get(),
            "the carried iterator resumes the traversal: completed invocations don't re-run, the in-flight " +
                "child invocation adopts its own capture, iteration 4 runs live exactly once")
        assertEquals(
            "199", resultDisplay(runId, nonCollectionDocumentPath),
            "item-correct child results [10, 20, 30, 40].sum() = 100 + the live Post edit (99)")
    }


    @Test
    fun completedLoopShortCircuitsWholesaleAcrossEdit() {
        val controller = context.serverLogicController

        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation)
        val edited = AutoTestUtils.graphDefinitionAttempt(
            edit(baseNotation, postLocation(forEachDocumentPath), "99"))

        val runId = controller.start(scriptLocation(forEachDocumentPath), base)
            ?: fail("Unable to start run")

        // Step until the whole loop is Done (its cursor cleared, its own outcome recorded) but Post has not run.
        val loopLocation = ObjectLocation(forEachDocumentPath, ObjectPath.parse("main.steps/Loop"))
        var guard = 0
        while (! isDone(runId, loopLocation) && guard < 100) {
            controller.step(runId, base)
            awaitState(LogicRunState.Paused)
            guard += 1
        }
        assertTrue(isDone(runId, loopLocation), "Loop should complete before the edit")
        assertEquals(4, CountingStep.count.get())

        runBlocking { controller.onStoreRefresh(edited) }
        controller.continueOrStart(runId, edited)
        awaitDone()

        assertEquals(
            4, CountingStep.count.get(),
            "a loop that completed pre-edit re-adopts its outcome wholesale — no iteration re-runs")
        assertEquals("199", resultDisplay(runId, forEachDocumentPath))
    }


    @Test
    fun secondEditMidLoopStillResumes() {
        val controller = context.serverLogicController

        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation)
        val editedNotation = edit(baseNotation, postLocation(forEachDocumentPath), "50")
        val edited = AutoTestUtils.graphDefinitionAttempt(editedNotation)
        val edited2 = AutoTestUtils.graphDefinitionAttempt(
            edit(editedNotation, postLocation(forEachDocumentPath), "99"))

        val runId = controller.start(scriptLocation(forEachDocumentPath), base)
            ?: fail("Unable to start run")

        // First edit mid-iteration-1: stepping after the edit migrates and re-parks at the same wavefront
        // (no re-execution) — proving the rebuilt run re-records the cursor at loop entry, so a SECOND
        // migration mid-iteration-2 still resumes.
        stepUntilCount(runId, base, 2)
        runBlocking { controller.onStoreRefresh(edited) }
        controller.step(runId, edited)
        awaitState(LogicRunState.Paused)
        assertEquals(2, CountingStep.count.get(), "migration re-park must not re-execute any iteration")

        stepUntilCount(runId, edited, 3)
        runBlocking { controller.onStoreRefresh(edited2) }
        controller.continueOrStart(runId, edited2)
        awaitDone()

        assertEquals(
            4, CountingStep.count.get(),
            "two pause -> edit -> resume cycles mid-loop compose: no iteration ever re-executes")
        assertEquals(
            "199", resultDisplay(runId, forEachDocumentPath),
            "outputs [10, 20, 30, 40].sum() = 100 + the final Post edit (99)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun scriptLocation(documentPath: DocumentPath): ObjectLocation {
        return ObjectLocation(documentPath, ObjectPath.parse("main"))
    }


    private fun postLocation(documentPath: DocumentPath): ObjectLocation {
        return ObjectLocation(documentPath, ObjectPath.parse("main.steps/Post"))
    }


    /**
     * Step (one boundary at a time, against [definition]) until [CountingStep] has run [target] times — the
     * park right after the Nth Count is at that iteration's Second boundary, i.e. mid-iteration.
     */
    private fun stepUntilCount(runId: LogicRunId, definition: GraphDefinitionAttempt, target: Int) {
        var guard = 0
        while (CountingStep.count.get() < target && guard < 100) {
            context.serverLogicController.step(runId, definition)
            awaitState(LogicRunState.Paused)
            guard += 1
        }
        assertEquals(target, CountingStep.count.get(), "expected to park right after Count invocation $target")
    }


    private fun edit(
        notation: GraphNotation,
        location: ObjectLocation,
        code: String
    ): GraphNotation {
        return NotationReducer()
            .applyStructural(
                notation,
                UpsertAttributeCommand(
                    location, AttributeName("code"), ScalarAttributeNotation(code)))
            .graphNotation
    }


    private fun isDone(runId: LogicRunId, location: ObjectLocation): Boolean {
        val trace = stepTrace(runId, location)
            ?: return false
        return trace.state == StepTrace.State.Done
    }


    private fun resultDisplay(runId: LogicRunId, documentPath: DocumentPath): Any? {
        val resultLocation = ObjectLocation(documentPath, ObjectPath.parse("main.steps/Result"))
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
