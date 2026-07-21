package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.objects.job.worker.test.ScratchProbeLog
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals


/**
 * End-to-end for the P4a scratch-dir mechanism: two Workers of one Job each resolve a private
 * [tech.kzen.auto.common.paradigm.job.control.JobControl.scratchDir], and the whole run tree is swept when the
 * run settles. Proves the [tech.kzen.auto.server.objects.job.service.JobWorkPool] →
 * [tech.kzen.auto.server.exec.job.EngineJobControl] path end-to-end (per-Worker isolation + run-root cleanup),
 * the foundation the file-backed Pivot / Explore Workers build on.
 *
 * The probe Workers each write a marker file into their dir, so a Success outcome already witnesses "the dir
 * existed and was writable during the run" (a bad path would throw and fail the run). Cleanup is verified AFTER
 * [RunEngine.await] returns because [RunEngine] disposes a node's Auto resources synchronously in `settleNode`
 * BEFORE it publishes the terminal outcome — so the sweep has provably run by the time `await` unblocks.
 */
class JobScratchDirTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/job-scratch-dir-test.yaml")
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
    fun eachWorkerGetsAnIsolatedScratchDirSweptWhenTheRunSettles() {
        ScratchProbeLog.reset()

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

        assertIs<Outcome.Success>(outcome)

        val probed = ScratchProbeLog.snapshot()
        assertEquals(setOf("source", "sink"), probed.keys, "both Workers should have probed a scratch dir")

        val sourceDir = Path.of(probed.getValue("source"))
        val sinkDir = Path.of(probed.getValue("sink"))

        // Isolation: distinct leaves (different Worker stable ids) that are siblings under one run dir.
        assertNotEquals(sourceDir, sinkDir, "each Worker must get its own scratch dir")
        assertEquals(sourceDir.parent, sinkDir.parent, "both Workers' dirs should be siblings under one run dir")

        // Swept: the run-root Auto resource deleted the whole run tree when the run settled.
        assertFalse(Files.exists(sourceDir), "source scratch dir should be swept after the run settles")
        assertFalse(Files.exists(sinkDir), "sink scratch dir should be swept after the run settles")
        assertFalse(Files.exists(sourceDir.parent), "the run scratch dir should be swept after the run settles")
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
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))

        return RunEngine(jobLogic, context.objectStableMapper.objectStableId(jobLocation))
    }
}
