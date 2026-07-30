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
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * Integration coverage for the PER-ITERATION trace reset (logic-spec §7 resettable live state) through
 * [ServerLogicController]: at each loop-iteration boundary the body steps' emitted trace values — and the
 * retained trace values of hosted child invocations the body's RunSteps launched — clear, so on iteration 2+
 * the sub-branch reads as not-yet-run instead of ghosting the previous iteration's finished values. The
 * append-only history (the film-strip, here [tech.kzen.auto.server.exec.script.test.BinaryDetailStep]'s
 * binary details) must survive every reset.
 *
 * Covers: inline body steps in the parent document ([inlineBodyStepTracesResetEachIteration]), a hosted child
 * sub-script's per-invocation buffers plus the surviving film-strip
 * ([hostedChildTraceValuesResetEachIterationEventsSurvive]), and composition with mid-loop live-edit
 * migration resume — the reset must not blank the replayed prefix ([resetComposesWithMigrationResume]).
 *
 * Sibling of [ServerLogicControllerLoopMigrationTest] (same driving pattern and helpers); relies on the
 * suite's sequential execution for the process-global [CountingStep] counter.
 */
class ServerLogicControllerLoopTraceResetTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val inlineDocumentPath = DocumentPath.parse("test/script/loop/script-loop-migration-test.yaml")
    private val hostedDocumentPath = DocumentPath.parse("test/script/loop/script-loop-trace-reset-test.yaml")
    private val hostedChildDocumentPath = DocumentPath.parse("test/script/loop/script-loop-trace-reset-child-test.yaml")

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
    fun inlineBodyStepTracesResetEachIteration() {
        val controller = context.serverLogicController

        val base = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())

        val runId = controller.start(scriptLocation(inlineDocumentPath), base)
            ?: fail("Unable to start run")

        // Park mid-iteration-3: Count has run 3 times, so iteration 3's boundary reset already dropped
        // iteration 2's finished values for the whole body.
        stepUntilCount(runId, base, 3)

        val secondLocation = ObjectLocation(inlineDocumentPath, ObjectPath.parse("main.steps/Loop.steps/Second"))
        val countLocation = ObjectLocation(inlineDocumentPath, ObjectPath.parse("main.steps/Loop.steps/Count"))
        assertNull(
            stepTrace(runId, secondLocation),
            "iteration 2's Second value must not ghost into iteration 3 — the boundary reset cleared it")
        assertTrue(
            isDone(runId, countLocation),
            "iteration 3's own completed Count shows Done (only the superseded pass's values cleared)")

        controller.continueOrStart(runId, base)
        awaitDone()

        assertEquals(4, CountingStep.count.get())
        assertEquals(
            "110", resultDisplay(runId, inlineDocumentPath),
            "the reset is display-only: outputs [10, 20, 30, 40].sum() = 100 + Post (10)")
    }


    @Test
    fun hostedChildTraceValuesResetEachIterationEventsSurvive() {
        val controller = context.serverLogicController

        val base = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())

        val runId = controller.start(scriptLocation(hostedDocumentPath), base)
            ?: fail("Unable to start run")

        // Park inside child invocation 3 (its Count done, its Snap boundary parked): iteration 3's boundary
        // reset already cleared invocations 1-2's retained buffers.
        stepUntilCount(runId, base, 3)

        val runStepLocation = ObjectLocation(hostedDocumentPath, ObjectPath.parse("main.steps/Loop.steps/Run"))
        val childExecutions = childExecutions(runId, runStepLocation)
        assertEquals(3, childExecutions.size, "one execution per child invocation so far")
        assertEquals(
            1, childExecutions.count { valueCount(it) > 0 },
            "only the LIVE invocation holds trace values — superseded iterations' buffers read as not-run")

        val afterLocation = ObjectLocation(hostedDocumentPath, ObjectPath.parse("main.steps/Loop.steps/After"))
        assertNull(
            stepTrace(runId, afterLocation),
            "the parent's own body step from iteration 2 must not ghost either")

        assertEquals(
            2, binaryEventCount(runId),
            "the film-strip retains each COMPLETED invocation's binary detail across the resets")

        controller.continueOrStart(runId, base)
        awaitDone()

        assertEquals(4, CountingStep.count.get())
        assertEquals("110", resultDisplay(runId, hostedDocumentPath))
        assertEquals(
            4, binaryEventCount(runId),
            "post-run, the film-strip holds every iteration's binary detail")
    }


    @Test
    fun resetComposesWithMigrationResume() {
        // The per-iteration reset must not blank the replayed prefix of a mid-loop live-edit resume: adopted
        // outcomes re-emit their traces, so after pause-inside-child -> edit -> resume the run completes with
        // the same counts / values as the pure migration test, and the final merged view shows the LAST
        // iteration's values.
        val controller = context.serverLogicController

        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation)
        val edited = AutoTestUtils.graphDefinitionAttempt(
            edit(baseNotation, ObjectLocation(hostedDocumentPath, ObjectPath.parse("main.steps/Post")), "99"))

        val runId = controller.start(scriptLocation(hostedDocumentPath), base)
            ?: fail("Unable to start run")

        stepUntilCount(runId, base, 3)

        runBlocking { controller.onStoreRefresh(edited) }
        controller.continueOrStart(runId, edited)
        awaitDone()

        assertEquals(
            4, CountingStep.count.get(),
            "resume semantics unchanged by the trace reset: no completed invocation re-runs")
        assertEquals(
            "199", resultDisplay(runId, hostedDocumentPath),
            "item-correct child results [10, 20, 30, 40].sum() = 100 + the live Post edit (99)")

        val childValLocation = ObjectLocation(hostedChildDocumentPath, ObjectPath.parse("main.steps/Val"))
        assertTrue(
            isDone(runId, childValLocation),
            "the final merged view shows the last invocation's completed values")
        assertEquals(
            4, binaryEventCount(runId),
            "each invocation's binary detail logged exactly once (inv 1-2 pre-edit, inv 3 post-resume, inv 4)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun scriptLocation(documentPath: DocumentPath): ObjectLocation {
        return ObjectLocation(documentPath, ObjectPath.parse("main"))
    }


    /** See [ServerLogicControllerLoopMigrationTest.stepUntilCount]. */
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


    /** Executions of the run launched from [callerLocation] (a RunStep) — one per child invocation. */
    private fun childExecutions(runId: LogicRunId, callerLocation: ObjectLocation): List<LogicRunExecutionId> {
        val callerStableId = context.objectStableMapper.objectStableId(callerLocation)
        return context.logicTrace.lookupRunExecutions(runId)
            .filter { it.callerStableId == callerStableId }
            .map { LogicRunExecutionId(runId, it.executionId) }
    }


    private fun valueCount(executionId: LogicRunExecutionId): Int {
        val snapshot = context.logicTrace.lookup(executionId, LogicTraceQuery(LogicTracePath.root))
            ?: return 0
        // Count LIVE emitted values only: a settled node always projects a terminal-outcome entry (the source
        // of the Job outcome chip), which is not a resettable live value and must not count here.
        return snapshot.values.keys.count { it.outcomeStableId() == null }
    }


    private fun binaryEventCount(runId: LogicRunId): Int {
        return context.logicTrace.lookupRunHistory(runId, 0).size
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
