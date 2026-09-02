package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.NodeStatus
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
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * End-to-end: load a real Job notation, translate it with [JobLogicCompiler], and run its concurrent Worker
 * graph on the [RunEngine] — the Job analogue of [tech.kzen.auto.server.exec.script.ScriptNotationTest] /
 * [tech.kzen.auto.server.exec.flow.FlowNotationTest]. Re-proves on the engine what
 * [tech.kzen.auto.server.objects.job.JobExecutionTest] drove against the retired re-entrant executor for the
 * core path: real CSV reader / expression filter / writer Workers streaming batched records over
 * order-synthesized Channels under the quiescence barrier, both run-to-completion and across a pause / resume.
 *
 * Jobs are nondeterministic, so assertions are on the drained output / terminal outcome — never interleaving
 * order or an exact pause count. Migration, the external duplex bridge, nested-logic (RunWorker), deadlock
 * detection and pause-on-error are intentionally not covered — they are tracked parity gaps for this first port
 * (see [JobRun]).
 */
class JobNotationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/job/job-engine-linear-test.yaml")
    private val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

    // The blank-port pipeline wires its reader/writer to these relative paths; the Workers resolve the same
    // relative strings against the test-JVM working directory, so test and Workers reach the same files.
    private val dir = Path.of("build/job-engine-linear")
    private val input = dir.resolve("input.csv")
    private val output = dir.resolve("output.csv")

    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun linearPipelineFiltersCsvToOutputFile() {
        // Reaching Success proves the whole stack on the engine: JobChannelSynthesis derived the two channels
        // from Worker order, JobChannelCreator wired each Worker a view of the shared channel, and the three
        // Workers streamed batched RecordBatches to completion as concurrent confined nodes. The output proves
        // the filter ran over the streamed batches (order preserved — single linear FIFO pipeline).
        val expectedKept = writeFlaggedInput(2_000)

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
        assertFilteredOutput(expectedKept)
    }


    @Test
    fun pausedPipelineResumesToFullOutput() {
        // A pre-armed step lands the pipeline at its first quiescent wavefront (every Worker parked at its first
        // checkpoint, nothing drained yet); resuming must complete it with the full, correct output — no records
        // lost or duplicated across the pause barrier. Proves the engine brings the concurrent Workers to a
        // coherent wavefront and resumes them cleanly (the Job analogue of Script/Flow pause-resume).
        val expectedKept = writeFlaggedInput(2_000)

        val engine = newEngine()
        val outcome =
            try {
                runBlocking {
                    engine.step()
                    engine.awaitQuiescent()

                    val snapshot = engine.snapshot()
                    assertTrue(
                        snapshot.root.status !is NodeStatus.Terminal,
                        "a stepped run must not have terminated at its first wavefront")
                    assertTrue(
                        snapshot.root.children.isNotEmpty() &&
                            snapshot.root.children.all { it.status is NodeStatus.Suspended },
                        "every Worker should be parked at its first checkpoint")

                    engine.resume()
                    engine.await()
                }
            }
            finally {
                engine.close()
            }

        assertIs<Outcome.Success>(outcome)
        assertFilteredOutput(expectedKept)
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


    // Writes an `id,flag,value` CSV alternating flag yes/no; returns the count of `yes` rows the filter keeps.
    private fun writeFlaggedInput(rows: Int): Int {
        Files.createDirectories(dir)
        var kept = 0
        Files.newBufferedWriter(input).use { writer ->
            writer.write("id,flag,value")
            writer.append('\n')
            for (i in 0 until rows) {
                val flag = if (i % 2 == 0) "yes" else "no"
                if (flag == "yes") {
                    kept += 1
                }
                writer.write("$i,$flag,v$i")
                writer.append('\n')
            }
        }
        Files.deleteIfExists(output)
        return kept
    }


    private fun assertFilteredOutput(expectedKept: Int) {
        val lines = Files.readAllLines(output)
        assertEquals("id,flag,value", lines.first())
        val dataLines = lines.drop(1)
        assertEquals(expectedKept, dataLines.size)
        assertTrue(dataLines.all { it.split(",")[1] == "yes" })
    }
}
