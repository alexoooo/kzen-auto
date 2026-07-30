package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * Engine-side coverage of the Job flavour's channel-aware deadlock detection ([JobDeadlockMonitor]): a Job whose
 * only Worker — a [CsvWriterWorker][tech.kzen.auto.server.objects.job.worker.CsvWriterWorker] — reads a Channel
 * no Worker ever produces to blocks forever on receive. The run goes quiescent (its dispatch task returned to the
 * pool) while neither paused nor complete, and it is NOT externally serviceable, so the monitor fails it rather
 * than letting [RunEngine.await] hang.
 *
 * This is the single-sink-on-orphan-channel case the retired engine `>= 2-leaf` topology watchdog MISSED (only
 * one blocked leaf), regained by moving detection to the channel-owning flavour. The healthy pipelines
 * ([JobNotationTest]), the pause/step wavefronts, the gated-latch migration barrier ([JobMigrationTest], where a
 * Worker parks on a non-channel latch), and the external serve bridge ([JobExternalBridgeTest]) must all continue
 * to run WITHOUT tripping the monitor — the precise `blocked-on-channel == active-workers` condition that this
 * test's positive case relies on is what spares those quiescent-but-not-deadlocked states.
 */
class JobDeadlockTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/job/channel/job-deadlock-csv-test.yaml")
    private val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun sinkOnUnfedChannelFailsAsDeadlock() {
        // The writer opens its output file in onStart, so the parent dir must exist — otherwise it fails THERE (a
        // plain failure, not the deadlock we mean to prove). With the file open, the writer's drive loop passes
        // its checkpoint and then blocks on hasNext() of the never-produced channel forever: a genuine deadlock
        // the monitor must report as Failed.
        Files.createDirectories(Path.of("build/job-deadlock-csv"))

        val engine = newEngine()
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

        val failed = assertIs<Outcome.Failed>(
            outcome, "a lone sink on an unfed channel should fail as a deadlock, not hang or complete")
        assertTrue(
            failed.message.contains("deadlock", ignoreCase = true),
            "the failure should name the deadlock (was: ${failed.message})")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun newEngine(): RunEngine {
        context = KzenAutoContext.forTest()

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
}
