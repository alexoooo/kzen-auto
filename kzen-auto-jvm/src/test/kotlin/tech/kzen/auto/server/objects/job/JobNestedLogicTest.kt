package tech.kzen.auto.server.objects.job

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.LogicExecutionFacade
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultCancelled
import tech.kzen.lib.common.exec.logic.model.LogicResultFailed
import tech.kzen.lib.common.exec.logic.model.LogicResultPaused
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.logic.context.MutableLogicControl
import tech.kzen.lib.server.exec.logic.context.MutableLogicResourceScope
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * Phase-2 nested Logic: a Worker may invoke another Logic as a child once per event, via the run-scoped
 * [tech.kzen.auto.common.paradigm.job.api.JobLogicHost] ([JobLogicHostImpl]) — the seam exposed on
 * [tech.kzen.auto.common.paradigm.job.control.JobControl.logicHost] and used by
 * [tech.kzen.auto.server.objects.job.worker.RunWorker].
 *
 * The flagged risk was concurrency: a top-level Script / Flow drives its child frames on the single controller
 * thread sharing one steppable [MutableLogicControl], but a Job runs its Workers concurrently. [JobLogicHostImpl]
 * resolves it by CONFINEMENT — each child runs on its OWN control + scope (its step state per-spine), sharing
 * only the run COMMAND (delegated to the shared control) plus the stateless GraphCreator + immutable definition.
 * [concurrentChildrenRunIsolated] is the proof for full speed; [steppingDescendsIntoNestedChildOneFreshStepAtATime]
 * proves a Step descends into a child via that child's own control (the per-spine budget granted by
 * [JobLogicHostImpl.grantStepToChildren]), with no shared stepping state.
 */
class JobNestedLogicTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val intChildDocumentPath = DocumentPath.parse("test/job-run-child-test.yaml")
    private val runWorkerDocumentPath = DocumentPath.parse("test/job-run-worker-test.yaml")

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
    fun concurrentChildrenRunIsolated() {
        // The crux of P2: many Workers hosting children at once must not interfere. Each thread runs the same
        // child Logic (number -> number * 10) with its own distinct inputs; every result must be that thread's
        // input * 10 (no cross-talk through the per-child private control / scope, and GraphCreator is safe to
        // call concurrently). The compiler cache is warmed first (one sequential run) so the burst exercises the
        // host's confinement, not the orthogonal cold-compile concurrency of the shared CachedKotlinCompiler.
        val host = newHost()
        val childLocation = ObjectLocation(intChildDocumentPath, ObjectPath.parse("main"))

        assertIs<LogicResultSuccess>(runToCompletion(host, childLocation, 0))  // warm the compiler cache

        val threadCount = 8
        val perThread = 8
        val executor = Executors.newFixedThreadPool(threadCount)
        try {
            val futures = (0 until threadCount).map { thread ->
                executor.submit(Callable {
                    val observed = mutableListOf<Pair<Int, Any?>>()
                    for (i in 0 until perThread) {
                        val input = thread * 1_000 + i
                        val result = runToCompletion(host, childLocation, input)
                        val success = result as? LogicResultSuccess
                            ?: error("expected success for $input, was $result")
                        observed.add(input to success.value.mainComponentValue())
                    }
                    observed
                })
            }

            val observed = futures.flatMap { it.get(60, TimeUnit.SECONDS) }
            assertEquals(threadCount * perThread, observed.size)
            for ((input, output) in observed) {
                assertEquals(input * 10, output, "child for input $input")
            }
        }
        finally {
            executor.shutdownNow()
        }
    }


    @Test
    fun recursivelyNestedChildRunsAndIsTraced() {
        // The reported regression: a Job Worker's child that ITSELF starts a further nested Logic. The chain is
        // host -> wrapper Script -> RunStep -> grandchild Script (number -> number*10); the inner RunStep is
        // exactly where the former NestedLogicUnsupported stub threw. Reaching Success with number*10 proves the
        // recursion runs and threads the result back through both levels.
        val runExecutionId = LogicRunExecutionId.random()
        val host = newHost(runExecutionId)

        val wrapperLocation = ObjectLocation(
            DocumentPath.parse("test/job-nested-wrapper-test.yaml"), ObjectPath.parse("main"))
        val grandchildLocation = ObjectLocation(intChildDocumentPath, ObjectPath.parse("main"))

        val result = runToCompletion(host, wrapperLocation, 5)

        val success = result as? LogicResultSuccess
            ?: error("expected success, was $result")
        assertEquals(50, success.value.mainComponentValue())

        // Both nested documents are now independently trace-recorded under the Job's run id (recursive
        // visibility), instead of running "dark" under the former NoOpLogicTraceHandle.
        assertNotNull(context.logicTraceStore.mostRecent(wrapperLocation), "wrapper trace")
        assertNotNull(context.logicTraceStore.mostRecent(grandchildLocation), "grandchild trace")

        val snapshot = context.logicTraceStore.lookupRun(
            runExecutionId.logicRunId, LogicTraceQuery(LogicTracePath.root))
        assertNotNull(snapshot, "run snapshot")
        assertTrue(snapshot.values.isNotEmpty(), "nested step values recorded in the run snapshot")
    }


    @Test
    fun steppingDescendsIntoNestedChildOneFreshStepAtATime() {
        // The step-into contract: with the run paused (command Pause, delegated to the child's own control),
        // each per-wavefront budget grant (grantStepToChildren) advances the child by exactly ONE fresh step —
        // descending through the wrapper's Run step into the grandchild before the chain completes — rather than
        // running the child straight through. This is the unit-level proof of what a Run Worker does when the
        // Job is stepped.
        val shared = MutableLogicControl(false)
        shared.commandPause()
        val host = newHost(sharedControl = shared)

        val wrapperLocation = ObjectLocation(
            DocumentPath.parse("test/job-nested-wrapper-test.yaml"), ObjectPath.parse("main"))

        // Warm the FormulaStep compiler cache via a separate full-speed run so the stepping assertions below
        // aren't timing-sensitive to first-compile.
        assertIs<LogicResultSuccess>(
            runToCompletion(newHost(), ObjectLocation(intChildDocumentPath, ObjectPath.parse("main")), 5))

        val facade = host.logicHandleFacade().start(wrapperLocation)
        try {
            assertTrue(facade.beforeStart(host.argumentTuple(wrapperLocation, 5)))

            // First fresh step: the wrapper's Run step descends into the grandchild, which then pauses before
            // its own first step (the tick's budget is already spent) — still paused, not yet complete.
            host.grantStepToChildren()
            assertIs<LogicResultPaused>(facade.continueOrStart(host.graphDefinition()))

            // Second fresh step: the grandchild's step runs, threading 5 -> 50 back up through both levels.
            host.grantStepToChildren()
            val second = facade.continueOrStart(host.graphDefinition())
            assertIs<LogicResultSuccess>(second)
            assertEquals(50, second.value.mainComponentValue())
        }
        finally {
            facade.close()
        }
    }


    @Test
    fun cancelAllShortCircuitsSubsequentChildren() {
        // Teardown / cancel of a Job aborts its hosted children: after cancelAll a new run short-circuits to
        // Cancelled rather than building + running a child.
        val host = newHost()
        val childLocation = ObjectLocation(intChildDocumentPath, ObjectPath.parse("main"))

        assertIs<LogicResultSuccess>(runToCompletion(host, childLocation, 1))

        host.cancelAll()

        assertEquals(LogicResultCancelled, runToCompletion(host, childLocation, 2))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun runWorkerExecutesChildPerBatchInJob() {
        // End-to-end through the real JobExecution -> JobControlImpl.logicHost() -> RunWorker path: CSV reader ->
        // RunWorker(child = RecordBatch identity Script) -> CSV writer. Reaching Success with output == input
        // proves a RunWorker hosts a child Logic per element inside a running Job and threads the result onward.
        val dir = Path.of("build/job-run-worker")
        Files.createDirectories(dir)
        Files.newBufferedWriter(dir.resolve("input.csv")).use {
            it.write("id,name"); it.newLine()
            it.write("0,a"); it.newLine()
            it.write("1,b"); it.newLine()
            it.write("2,c"); it.newLine()
        }
        Files.deleteIfExists(dir.resolve("output.csv"))

        val mainLocation = ObjectLocation(runWorkerDocumentPath, ObjectPath.parse("main"))
        val execution = AutoTestUtils.liveLogicExecution(context, mainLocation, UnusedLogicHandle)
        execution.beforeStart(TupleValue.empty)

        // The FULL definition (not filtered to the Job's document), so the host can resolve the child Script's
        // own document — mirrors what ServerLogicController passes in production.
        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), fullGraphDefinition())

        assertIs<LogicResultSuccess>(result)
        assertEquals(
            listOf("id,name", "0,a", "1,b", "2,c"),
            Files.readAllLines(dir.resolve("output.csv")))
    }


    @Test
    fun steppingRunWorkerDescendsIntoChildWithinJob() {
        // Integration through the real JobExecution -> JobControlImpl.logicHost() -> RunWorker path, STEPPED.
        // The child is two-step, so a single step leaves the first child mid-execution and emits nothing — the
        // tell that a Step descends INTO the RunWorker's child rather than running the whole child per step (the
        // old behaviour would have completed the first child and written a row after one step). Stepping on to
        // completion still yields identity output, so correctness holds across the stepped run.
        val dir = Path.of("build/job-run-worker-stepping")
        Files.createDirectories(dir)
        Files.newBufferedWriter(dir.resolve("input.csv")).use {
            it.write("id,name"); it.newLine()
            it.write("0,a"); it.newLine()
            it.write("1,b"); it.newLine()
            it.write("2,c"); it.newLine()
        }
        Files.deleteIfExists(dir.resolve("output.csv"))

        val mainLocation = ObjectLocation(
            DocumentPath.parse("test/job-run-worker-stepping-test.yaml"), ObjectPath.parse("main"))
        val execution = AutoTestUtils.liveLogicExecution(context, mainLocation, UnusedLogicHandle)
        execution.beforeStart(TupleValue.empty)

        val control = MutableLogicControl(false)
        val resourceScope = MutableLogicResourceScope()
        val graphDefinition = fullGraphDefinition()

        // Park the fresh workers at their first checkpoint.
        control.commandPause()
        assertIs<LogicResultPaused>(
            execution.continueOrStart(control, resourceScope, graphDefinition))

        // One step: the reader emits a batch and the RunWorker descends one step into the two-step child, now
        // mid-execution — so nothing has reached the writer yet.
        control.arm(1)
        assertIs<LogicResultPaused>(
            execution.continueOrStart(control, resourceScope, graphDefinition))
        val outputFile = dir.resolve("output.csv")
        val rowsAfterOneStep =
            if (Files.exists(outputFile)) Files.readAllLines(outputFile).drop(1).size else 0
        assertEquals(0, rowsAfterOneStep, "one step must not run the whole child to completion")

        // Step on to completion: identity output (same rows as input) confirms correctness across stepping.
        var result: LogicResult
        var guard = 0
        do {
            control.arm(1)
            result = execution.continueOrStart(control, resourceScope, graphDefinition)
            guard += 1
        } while (result is LogicResultPaused && guard < 1_000)

        assertIs<LogicResultSuccess>(result)
        assertEquals(
            listOf("id,name", "0,a", "1,b", "2,c"),
            Files.readAllLines(outputFile))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun newHost(
        runExecutionId: LogicRunExecutionId = LogicRunExecutionId.random(),
        sharedControl: MutableLogicControl = MutableLogicControl(false)
    ): JobLogicHostImpl {
        return JobLogicHostImpl(
            fullGraphDefinition(),
            runExecutionId,
            context.graphCreator,
            context.graphEnvironment,
            context.logicTraceStore,
            // No controller run is active in this direct-drive test, so the registry no-ops (frames need a
            // live run); the assertions here are on traces / step results, recorded regardless.
            context.serverLogicController,
            sharedControl)
    }


    // Drive a child to completion on a full-speed shared control (command None): one continueOrStart runs it
    // through. Mirrors how RunWorker drives a child, minus the per-wavefront checkpoint. The standalone
    // step-into behaviour is covered by [steppingDescendsIntoNestedChildOneFreshStepAtATime].
    private fun runToCompletion(host: JobLogicHostImpl, child: ObjectLocation, input: Any?): LogicResult {
        val facade = host.logicHandleFacade().start(child)
        try {
            if (! facade.beforeStart(host.argumentTuple(child, input))) {
                return LogicResultFailed("Unable to initialize $child")
            }
            var result: LogicResult
            do {
                result = facade.continueOrStart(host.graphDefinition())
            }
            while (result is LogicResultPaused)
            return result
        }
        finally {
            facade.close()
        }
    }


    // The whole successful definition, unfiltered, so any document's Logic (the children) is resolvable.
    private fun fullGraphDefinition(): GraphDefinition {
        return AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())
            .transitiveSuccessful
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The Job's Workers reach nested logic through JobControl.logicHost(), not this handle, so it is unused.
    private object UnusedLogicHandle: LogicHandle {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation
        ): LogicExecutionFacade =
            error("nested logic should be hosted via JobControl.logicHost() for a Job")
    }
}
