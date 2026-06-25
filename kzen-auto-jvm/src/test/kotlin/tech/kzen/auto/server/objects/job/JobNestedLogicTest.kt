package tech.kzen.auto.server.objects.job

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.LogicExecutionFacade
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.model.LogicResultCancelled
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
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


/**
 * Phase-2 nested Logic: a Worker may invoke another Logic as a child once per event, via the run-scoped
 * [tech.kzen.auto.common.paradigm.job.api.JobLogicHost] ([JobLogicHostImpl]) — the seam exposed on
 * [tech.kzen.auto.common.paradigm.job.control.JobControl.logicHost] and used by
 * [tech.kzen.auto.server.objects.job.worker.RunWorker].
 *
 * The flagged risk was concurrency: a top-level Script / Flow drives its child frames on the single controller
 * thread sharing one steppable [MutableLogicControl], but a Job runs its Workers concurrently. [JobLogicHostImpl]
 * resolves it by CONFINEMENT — each child runs full-speed on a private control + scope, sharing only the
 * stateless GraphCreator + immutable definition. [concurrentChildrenRunIsolated] is the proof: many threads run
 * the same child with distinct inputs at once and every result is correct and isolated.
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

        assertIs<LogicResultSuccess>(host.run(childLocation, 0))  // warm the compiler cache

        val threadCount = 8
        val perThread = 8
        val executor = Executors.newFixedThreadPool(threadCount)
        try {
            val futures = (0 until threadCount).map { thread ->
                executor.submit(Callable {
                    val observed = mutableListOf<Pair<Int, Any?>>()
                    for (i in 0 until perThread) {
                        val input = thread * 1_000 + i
                        val result = host.run(childLocation, input)
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
    fun cancelAllShortCircuitsSubsequentChildren() {
        // Teardown / cancel of a Job aborts its hosted children: after cancelAll a new run short-circuits to
        // Cancelled rather than building + running a child.
        val host = newHost()
        val childLocation = ObjectLocation(intChildDocumentPath, ObjectPath.parse("main"))

        assertIs<LogicResultSuccess>(host.run(childLocation, 1))

        host.cancelAll()

        assertEquals(LogicResultCancelled, host.run(childLocation, 2))
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


    //-----------------------------------------------------------------------------------------------------------------
    private fun newHost(): JobLogicHostImpl {
        return JobLogicHostImpl(
            fullGraphDefinition(),
            LogicRunExecutionId.random(),
            context.graphCreator,
            context.graphEnvironment)
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
