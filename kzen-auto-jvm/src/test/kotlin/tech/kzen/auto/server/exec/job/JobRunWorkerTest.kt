package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.script.test.FlakyStep
import tech.kzen.auto.server.exec.script.test.ScriptStepTestModule
import tech.kzen.auto.server.objects.job.worker.test.RecordingSinkWorker
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Node
import tech.kzen.lib.common.exec.engine.NodeStatus
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.engine.PauseReason
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * End-to-end for the nested-Logic [RunWorker][tech.kzen.auto.server.objects.job.worker.RunWorker] on the engine:
 * a Job whose middle Worker hosts a child Script once per incoming element, via the extensible
 * [JobControl.host][tech.kzen.auto.common.paradigm.job.control.JobControl.host] seam. Covers the two capabilities
 * Phase B re-ports: hosting a child Logic per element (run + step), and Job pause-on-error reaching a Worker's
 * hosted child (park Suspended(Error) then fix-free resume to success, or fail the run when the toggle is off).
 *
 * The Worker graph is order-synthesized (source.output -> run.input, run.output -> sink.input); the RunWorker
 * and FormulaSource archetypes are production, the RecordingSink and the child's FlakyStep are test-only classes
 * (the sink served by the JVM reflective mirror, the step registered by hand via [ScriptStepTestModule]). Jobs
 * are nondeterministic, so assertions
 * are on the drained output / terminal outcome, never interleaving order.
 */
class JobRunWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun runsChildLogicPerElement() {
        // FormulaSource emits 1..3; the RunWorker hosts the child Script (number + 1) once per element and emits
        // its result downstream. That the sink records {2, 3, 4} proves the child Logic ran per element through
        // the host seam — the RunWorker composed a reusable sub-Logic into the Job's dataflow.
        val engine = newEngine("test/job/run/job-run-host-test.yaml")
        val outcome =
            try {
                runBlocking {
                    engine.resume()
                    engine.await()
                }
            }
            finally {
                engine.close()
            }

        assertIs<Outcome.Success>(outcome)
        assertEquals(3, RecordingSinkWorker.recorded().size)
        assertEquals(setOf(2, 3, 4), RecordingSinkWorker.recorded().toSet())
    }


    @Test
    fun steppedRunHostingAChildResumesToFullOutput() {
        // A pre-armed step lands the Job at its first quiescent wavefront (it must not have run to completion);
        // resuming drives the RunWorker's hosted children to full, correct output. Proves stepping crosses the
        // host boundary uniformly for a Job Worker exactly as it does for a Script RunStep.
        val engine = newEngine("test/job/run/job-run-host-test.yaml")
        val outcome =
            try {
                runBlocking {
                    engine.step()
                    engine.awaitQuiescent()
                    assertTrue(
                        engine.snapshot().root.status !is NodeStatus.Terminal,
                        "a stepped run must not have terminated at its first wavefront")

                    engine.resume()
                    engine.await()
                }
            }
            finally {
                engine.close()
            }

        assertIs<Outcome.Success>(outcome)
        assertEquals(setOf(2, 3, 4), RecordingSinkWorker.recorded().toSet())
    }


    @Test
    fun childFailureUnderPauseOnErrorParksThenResumesToSuccess() {
        // The first hosted child's FlakyStep throws. With pause-on-error on, the child's recoverable boundary
        // parks it Suspended(Error) and the engine's centrally-coordinated pause brings the whole Job to a
        // quiescent paused wavefront rather than failing — a child breakpoint IS a run-wide pause, with no
        // explicit halt request. A plain resume re-runs the (now-cleared) step to success and the remaining
        // elements process, so all three reach the sink.
        val engine = newEngine("test/job/run/job-run-flaky-test.yaml")
        val outcome =
            try {
                runBlocking {
                    engine.pauseOnError(true)
                    engine.resume()
                    engine.awaitQuiescent()

                    assertTrue(
                        engine.snapshot().root.anyErrorParked(),
                        "the hosted child's failure should park the run Suspended(Error)")
                    assertTrue(
                        engine.snapshot().root.status !is NodeStatus.Terminal,
                        "an error-paused run must still be active")

                    engine.resume()
                    engine.await()
                }
            }
            finally {
                engine.close()
            }

        assertIs<Outcome.Success>(outcome)
        assertEquals(3, RecordingSinkWorker.recorded().size)
    }


    @Test
    fun childFailureWithoutPauseOnErrorFailsTheRun() {
        // With pause-on-error off (the default), the hosted child's failure propagates through the RunWorker and
        // fails the whole Job — the same terminal semantics as any other failing Worker.
        val engine = newEngine("test/job/run/job-run-flaky-test.yaml")
        val outcome =
            try {
                runBlocking {
                    engine.resume()
                    engine.await()
                }
            }
            finally {
                engine.close()
            }

        assertIs<Outcome.Failed>(outcome)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun newEngine(path: String): RunEngine {
        RecordingSinkWorker.reset()
        FlakyStep.reset()
        ScriptStepTestModule.register()

        context = KzenAutoContext.forTest()

        val documentPath = DocumentPath.parse(path)
        val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val jobLogic = JobLogicCompiler.compile(
            jobLocation,
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

        return RunEngine(jobLogic, context.objectStableMapper.objectStableId(jobLocation))
    }


    // True when this node or any descendant is parked Suspended(Error) — the deepest-failure marker the
    // controller maps to LogicRunState.ErrorPaused; here the hosted child, not the Job root, is the one parked.
    private fun Node.anyErrorParked(): Boolean {
        return status == NodeStatus.Suspended(PauseReason.Error) ||
            children.any { it.anyErrorParked() }
    }
}
