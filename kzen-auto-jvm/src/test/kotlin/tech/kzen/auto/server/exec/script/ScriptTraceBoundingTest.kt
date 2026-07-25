package tech.kzen.auto.server.exec.script

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.objects.document.script.model.ForEachProgress
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.common.util.TraceDisplay
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Node
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * Trace bounding (script-improvements phase 7): a long or large-valued run must not grow the trace, the history,
 * or the wire without bound. Runs real Script notation on a real [RunEngine] and reads the engine directly, so
 * the assertions are about what is actually retained rather than about a projection of it.
 */
class ScriptTraceBoundingTest {
    //-----------------------------------------------------------------------------------------------------------------
    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-------------------------------------------------------------------------------------- referenced-aware collection
    @Test
    fun unreferencedLoopCollectsNothing() {
        runScript("test/script-loop-collection-test.yaml") { engine, documentPath ->
            assertEquals(
                "[]",
                displayOf(engine, documentPath, "main.steps/Loop"),
                "nothing reads the loop, so it must not collect its iterations' values")
        }
    }


    @Test
    fun referencedLoopStillCollects() {
        // The same shape, except `Total` reads `Loop.sum()` — the guard that the elision is scoped to loops
        // whose value is genuinely unread.
        runScript("test/script-engine-foreach-test.yaml") { engine, documentPath ->
            assertEquals(
                "[2, 4, 6]",
                displayOf(engine, documentPath, "main.steps/Loop"),
                "a referenced loop must still collect every iteration's value")
        }
    }


    @Test
    fun branchTerminalLoopCollectsButRootTerminalLoopDoesNot() {
        runScript("test/script-loop-collection-nested-test.yaml") { engine, documentPath ->
            // `Inner` terminates `Outer`'s body branch, so its value IS read — it is what `Outer` would collect.
            // (Its live trace is the last outer iteration's, per the per-iteration trace reset: Outer Item = 2.)
            assertEquals(
                "[2, 4]",
                displayOf(engine, documentPath, "main.steps/Outer.steps/Inner"),
                "a branch terminal's value becomes its container's value, so it must collect")

            assertEquals(
                "[]",
                displayOf(engine, documentPath, "main.steps/Outer"),
                "the root list's terminal value is discarded by ScriptLogic, so the outer loop must not collect")
        }
    }


    //------------------------------------------------------------------------------------------------ history bounding
    @Test
    fun longLoopAppendsNothingToHistory() {
        runScript("test/script-loop-history-test.yaml") { engine, documentPath ->
            // The headline: Script's step traces are transient, so a 10k-iteration loop — which would previously
            // have retained ~40k events — appends nothing. The film strip (log-style events, null address) is
            // unaffected; this script simply produces none, so history is empty outright.
            val emitted = engine.history(0).count { it.address != null }
            assertEquals(0, emitted, "Script step traces must not be appended to the run's history")
            assertEquals(0, engine.history(0).size, "no film-strip events either — this script logs nothing")

            // Live trace still works: transient means "not retained", not "not published".
            assertEquals("[]", displayOf(engine, documentPath, "main.steps/Loop"))
        }
    }


    //--------------------------------------------------------------------------------------------- display truncation
    @Test
    fun bigValueDisplayIsTruncatedWhileTheValueItselfIsNot() {
        runScript("test/script-display-truncation-test.yaml") { engine, documentPath, outcome ->
            // The value graph is untouched: `Result` reads Big.length and sees the whole 10000 chars.
            assertEquals(10_000, assertIs<Outcome.Success>(outcome).value.mainComponentValue())

            val display = displayOf(engine, documentPath, "main.steps/Big")
            val expectedRemaining = 10_000 - TraceDisplay.maxScriptTraceChars
            assertEquals(
                "x".repeat(TraceDisplay.maxScriptTraceChars) + "… ($expectedRemaining more chars)",
                display)
            assertTrue(
                display.length < 10_000,
                "the display must be bounded, not the value")
        }
    }


    //----------------------------------------------------------------------------------------- ForEach value journal
    @Test
    fun unreferencedLoopStillJournalsItsValues() {
        // The counterpart to [unreferencedLoopCollectsNothing]: the loop collects no VALUES, but the display
        // journal is kept regardless, so the card can still show what each iteration produced.
        runScript("test/script-loop-collection-test.yaml") { engine, documentPath ->
            val progress = progressOf(engine, documentPath, "main.steps/Loop")

            assertEquals(3, progress.producedCount)
            assertEquals(
                listOf("1" to "2", "2" to "4", "3" to "6"),
                progress.produced.map { it.item to it.value },
                "every iteration is journalled even though the loop collected nothing")
            assertEquals(
                2, progress.index,
                "the completed loop's counter still names its LAST iteration (the exit re-emit)")
            assertNull(progress.size, "an IntRange is an Iterable, not a Collection — no known total")
            assertFalse(progress.partial)
        }
    }


    @Test
    fun longLoopJournalIsCapped() {
        runScript("test/script-loop-history-test.yaml") { engine, documentPath ->
            val progress = progressOf(engine, documentPath, "main.steps/Loop")

            assertEquals(10_000, progress.producedCount, "the total is untruncated")
            assertEquals(
                ForEachProgress.maxProducedEntries, progress.produced.size,
                "a 10k-iteration loop must not put 10k entries into every client poll")
            assertEquals(
                ForEachProgress.Entry("10000", "20000"), progress.produced.last(),
                "the journal keeps the most RECENT iterations")
            assertEquals(10_000 - ForEachProgress.maxProducedEntries, progress.omittedCount())
        }
    }


    //----------------------------------------------------------------------------------------------------- internals
    // The step's live trace display, read off the engine's node tree exactly as a trace query would.
    private fun displayOf(engine: RunEngine, documentPath: DocumentPath, objectPath: String): String {
        return (traceOf(engine, documentPath, objectPath).displayValue as TextExecutionValue).value
    }


    private fun progressOf(engine: RunEngine, documentPath: DocumentPath, objectPath: String): ForEachProgress {
        return ForEachProgress.ofExecutionValueOrNull(traceOf(engine, documentPath, objectPath).detail)
            ?: error("No ForEach progress traced for: $objectPath")
    }


    private fun traceOf(engine: RunEngine, documentPath: DocumentPath, objectPath: String): StepTrace {
        val stableId = context.objectStableMapper.objectStableId(
            ObjectLocation(documentPath, ObjectPath.parse(objectPath)))

        val value = findLive(engine.snapshot().root, Address.of(stableId.value))
            ?: error("No trace emitted for: $objectPath")

        return StepTrace.ofExecutionValue(value)
    }


    private fun findLive(node: Node, address: Address): tech.kzen.lib.common.exec.ExecutionValue? {
        node.live[address]?.let { return it }
        for (child in node.children) {
            findLive(child, address)?.let { return it }
        }
        return null
    }


    private fun runScript(
        documentPathString: String,
        assertions: (RunEngine, DocumentPath, Outcome) -> Unit
    ) {
        context = KzenAutoContext.forTest()

        val documentPath = DocumentPath.parse(documentPathString)
        val scriptLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val scriptLogic = ScriptLogicCompiler.compile(
            scriptLocation,
            graphNotation,
            graphDefinition,
            LogicCompilerServices(
                context.graphEnvironment,
                context.objectStableMapper,
                context.cachedKotlinCompiler,
                context.scriptValidationCache,
                context.jobValidationCache,
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))

        val engine = RunEngine(scriptLogic, context.objectStableMapper.objectStableId(scriptLocation), TupleValue.empty)
        try {
            val outcome = runBlocking {
                engine.resume()
                engine.await()
            }
            assertIs<Outcome.Success>(outcome, "run failed: $outcome")

            // Read the engine while it is still open — the trace lives in it (there is no separate store).
            assertions(engine, documentPath, outcome)
        }
        finally {
            engine.close()
        }
    }


    private fun runScript(documentPathString: String, assertions: (RunEngine, DocumentPath) -> Unit) {
        runScript(documentPathString) { engine, documentPath, _ -> assertions(engine, documentPath) }
    }
}
