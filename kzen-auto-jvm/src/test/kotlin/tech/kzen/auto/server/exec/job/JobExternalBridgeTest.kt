package tech.kzen.auto.server.exec.job

import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.server.exec.engine.RunEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * Engine-side coverage of the Job flavour's external duplex request bridge (logic-spec §4): the browser->worker
 * PULL path. A [tech.kzen.auto.server.objects.job.worker.PreviewWorker] serves slice queries over an EXTERNAL
 * duplex Channel; a UI request addressed by the channel name reaches the serving Worker through [JobRun]'s
 * [tech.kzen.lib.common.exec.engine.Execution.onRequest] router (registered on the Job's ROOT node, which is the
 * frame the JS addresses) and returns the live slice. Re-proves on the new engine what the retired
 * `JobExecution.route` / `externalClients` gave.
 *
 * Driven the way the controller is: step the Job to a paused mid-stream wavefront (so the Preview has rows but
 * the run is still live and its serve loop responsive), then call [RunEngine.request] on the root node with the
 * same `channel` / `offset` / `limit` parameters the JS sends. A served slice with the live row count proves the
 * request crossed the bridge to the serving Worker rather than hitting an empty / stale handler.
 */
class JobExternalBridgeTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/job-bridge-test.yaml")
    private val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
    private val previewWorkerLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/preview"))
    private val inputDir = Path.of("build/job-bridge")
    private val inputCsv = inputDir.resolve("input.csv")

    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun externalBridgeServesLivePreviewSliceWhileRunning() {
        val rows = 100
        writeCsv(inputCsv, rows)
        context = KzenAutoContext.forTest()

        val notation = AutoTestUtils.readNotation()
        val logic = compile(notation)

        val engine = RunEngine(logic, context.objectStableMapper.objectStableId(jobLocation))
        try {
            // Step to a paused wavefront where the Preview has consumed at least one batch — so it has rows to
            // serve and the run is still live (its serve loop is a sibling coroutine, responsive while paused).
            var guard = 0
            do {
                engine.step()
                engine.awaitQuiescent()
                guard += 1
            }
            while (previewCount(engine) == 0L && guard < 300)
            val liveCount = previewCount(engine)
            assertTrue(liveCount > 0L, "Preview should consume at least one batch before the query (was $liveCount)")

            // The exact request the JS sends for a manually-declared serve channel (channel name = leaf "queries").
            val request = ExecutionRequest(
                RequestParams.of(
                    JobConventions.channelParameter to "queries",
                    JobConventions.previewOffsetParameter to "0",
                    JobConventions.previewLimitParameter to "200"),
                null)

            // The JS addresses the run's frame, which is the root node (LogicRunInfo.frame = root).
            val result = engine.request(engine.snapshot().root.id, request)

            val success = assertIs<ExecutionSuccess>(
                result, "the bridge routes the request to the serving Worker and returns its reply")
            @Suppress("UNCHECKED_CAST")
            val slice = success.value.get() as Map<String, Any?>
            assertEquals(listOf("id", "name"), slice["header"])
            assertTrue(
                (slice["rows"] as List<*>).isNotEmpty(),
                "the bridge serves the live preview slice (non-empty rows)")
            assertEquals(
                liveCount, slice["count"],
                "the served slice reflects the Worker's LIVE snapshot (count matches its progress), proving the " +
                    "request crossed the bridge rather than hitting an empty handler")
        }
        finally {
            engine.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun compile(notation: GraphNotation): JobLogic {
        val definition = AutoTestUtils.graphDefinitionAttempt(notation).transitiveSuccessful
        return JobLogicCompiler.compile(
            jobLocation,
            notation,
            definition,
            LogicCompilerServices(
                context.graphEnvironment,
                context.objectStableMapper,
                context.cachedKotlinCompiler,
                context.scriptValidationCache,
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))
    }


    // The Preview Worker's live count, read from its node's progress emit (the in-tree analogue of the trace path
    // the JS Job UI polls) — the same source JobMigrationTest reads.
    private fun previewCount(engine: RunEngine): Long {
        val previewStableId = context.objectStableMapper.objectStableId(previewWorkerLocation)
        val previewNode = engine.snapshot().root.children
            .firstOrNull { it.stableId == previewStableId }
            ?: return 0L
        val progress = previewNode.live[Address.of(EngineJobControl.workerProgressAddressMarker)]?.get() as? Map<*, *>
            ?: return 0L
        return progress["count"] as? Long ?: 0L
    }


    private fun writeCsv(path: Path, rows: Int) {
        Files.createDirectories(path.parent)
        Files.newBufferedWriter(path).use { writer ->
            writer.write("id,name")
            writer.newLine()
            for (i in 0 until rows) {
                writer.write("$i,n$i")
                writer.newLine()
            }
        }
    }
}
