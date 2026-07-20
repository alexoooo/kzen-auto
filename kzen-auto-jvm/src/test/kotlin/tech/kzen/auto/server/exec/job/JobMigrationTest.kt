package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.objects.job.worker.test.GatedCountingSinkWorker
import tech.kzen.auto.server.objects.job.worker.test.GatedSourceWorker
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.server.exec.engine.RunEngine
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * Engine-side coverage of the Job flavour's live-edit state migration (logic-spec §5): pause -> edit config ->
 * resume, driven directly through [RunEngine.migrate]. Re-proves on the new single-writer engine what
 * [tech.kzen.auto.server.objects.job.JobStateMigrationTest] /
 * [tech.kzen.auto.server.objects.job.JobMigrationCarryoverTest] drove against the retired re-entrant
 * `JobExecution.migrate`: a same-stable-id Worker resumes from its carried run-scoped state ([WorkerLogic]
 * bridging [tech.kzen.auto.server.objects.job.worker.WorkerBase.captureMigrationState] /
 * `loadMigrationState`), and a channel's in-flight payloads survive the rebuild ([JobRun] draining /
 * preloading [tech.kzen.auto.server.objects.job.channel.JobChannel] by stable id).
 *
 * The edit is applied by recompiling a second [JobLogic] from the edited notation and handing it to
 * [RunEngine.migrate] at the quiescent (paused) barrier — exactly what the controller's edit-detection will do
 * once it is wired. Both assertions are EXACT and timing-independent: a dropped in-flight payload falls short, a
 * restart-instead-of-resume overshoots, so only a lossless migration hits the number.
 */
class JobMigrationTest {
    //-----------------------------------------------------------------------------------------------------------------
    // Preview-pipeline fixture (shared with JobStateMigrationTest): CsvReaderWorker -> raw channel -> PreviewWorker.
    private val previewDocumentPath = DocumentPath.parse("test/job-migration-preview-test.yaml")
    private val previewJobLocation = ObjectLocation(previewDocumentPath, ObjectPath.parse("main"))
    private val previewWorkerLocation = ObjectLocation(previewDocumentPath, ObjectPath.parse("main.workers/preview"))
    private val previewDir = Path.of("build/job-migration")
    private val previewInput = previewDir.resolve("input.csv")

    // Gated channel-carryover fixture (shared with JobMigrationCarryoverTest): GatedSourceWorker -> buffered
    // channel -> GatedCountingSinkWorker (first instance never drains). Must match the fixture's `buffer`/`total`.
    private val carryoverDocumentPath = DocumentPath.parse("test/job-migration-carryover-test.yaml")
    private val carryoverJobLocation = ObjectLocation(carryoverDocumentPath, ObjectPath.parse("main"))
    private val carryoverSinkLocation = ObjectLocation(carryoverDocumentPath, ObjectPath.parse("main.workers/sink"))
    private val channelBuffer = 4
    private val sourceTotal = 50

    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun migrationResumesReaderAndCarriesPreviewStateLosslessly() {
        // Mirror of JobStateMigrationTest.editingNonReaderConfigResumesReaderFromItsPosition: editing a NON-reader
        // attribute (the Preview's sample size) leaves the reader's config unchanged, so on migrate the reader
        // RESUMES from its file position and the Preview ADOPTS its carried count, while the channel carries any
        // in-flight batch. Every row is then counted exactly once -> the final count equals the row total. Without
        // reader-resume the file is re-read on top of the carried count (overshoot); without channel carryover an
        // in-flight batch is dropped (shortfall).
        val rows = 100
        writeCsv(previewInput, rows)
        context = KzenAutoContext.forTest()

        val notation = AutoTestUtils.readNotation()
        val baseLogic = compile(previewJobLocation, notation)
        val editedLogic = compile(
            previewJobLocation,
            edit(notation, previewWorkerLocation, "sample", "500"))

        val engine = RunEngine(baseLogic, context.objectStableMapper.objectStableId(previewJobLocation))
        try {
            // Step to a paused wavefront where the Preview has consumed at least one batch — so the reader's file
            // position is past the start and resume-vs-restart is observable.
            var guard = 0
            do {
                engine.step()
                engine.awaitQuiescent()
                guard += 1
            }
            while (previewCount(engine) == 0L && guard < 200)
            assertTrue(previewCount(engine) > 0L, "Preview should consume at least one batch before the edit")

            // Resume against the edited definition: the reader resumes from position, the Preview carries its
            // count, and the channel carries any buffered / parked-mid-send batch.
            engine.migrate(editedLogic, paused = false)
            val outcome = runBlocking { engine.await() }

            assertIs<Outcome.Success>(outcome)
            assertEquals(
                rows.toLong(), previewCount(engine),
                "reader resumes from position and the migration carries any in-flight batch, so every row is " +
                    "counted exactly once (a restart would exceed $rows; a dropped batch would fall short)")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun migrationCarriesBufferedAndParkedChannelPayloadsLosslessly() {
        // Mirror of JobMigrationCarryoverTest: at the pause cut the channel holds `channelBuffer` buffered payloads
        // PLUS one the source is parked mid-send on. The migrate must carry ALL of them into the rebuilt graph
        // (JobChannel.drainBuffered / preload), the source must resume from its position (not re-send), and the
        // rebuilt ungated sink drains carryover then the remainder — so the total received equals the source total.
        GatedSourceWorker.reset()
        GatedCountingSinkWorker.reset()
        context = KzenAutoContext.forTest()

        val notation = AutoTestUtils.readNotation()
        val baseLogic = compile(carryoverJobLocation, notation)
        // A no-op change to the SINK's `note` trips the rebuild without touching the source's config.
        val editedLogic = compile(
            carryoverJobLocation,
            edit(notation, carryoverSinkLocation, "note", "edited"))

        val engine = RunEngine(baseLogic, context.objectStableMapper.objectStableId(carryoverJobLocation))
        try {
            // The gated sink (#1) never drains, so the source fills the buffer and parks mid-send at exactly
            // buffer + 1 sends — a stable state the engine quiesces at (no deadlock detection in this port).
            engine.resume()
            val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (GatedSourceWorker.sendsStarted.get() < channelBuffer + 1) {
                assertTrue(System.nanoTime() < deadlineNanos, "source never filled the channel buffer")
                Thread.sleep(1)
            }

            // Pause + quiesce on the stable "buffer full + one parked send" state. Pausing FIRST is what makes the
            // capture safe: a source the channel drain unparks re-parks at its next checkpoint instead of running
            // away producing more rows.
            engine.pause()
            engine.awaitQuiescent()
            assertEquals(
                (channelBuffer + 1).toLong(), GatedSourceWorker.sendsStarted.get().toLong(),
                "source parks after exactly buffer + 1 sends (buffer buffered, one parked mid-send)")
            assertEquals(
                0L, GatedCountingSinkWorker.received.get(),
                "the gated first sink instance consumed nothing before the migration")

            engine.migrate(editedLogic, paused = false)
            val outcome = runBlocking { engine.await() }

            assertIs<Outcome.Success>(outcome)
            assertEquals(
                sourceTotal.toLong(), GatedCountingSinkWorker.received.get(),
                "every row delivered exactly once: a dropped in-flight payload would fall short of $sourceTotal, " +
                    "a restart-instead-of-resume would exceed it")
        }
        finally {
            engine.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun compile(jobLocation: ObjectLocation, notation: GraphNotation): JobLogic {
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
                context.flowMessageInspector,
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))
    }


    private fun edit(
        notation: GraphNotation,
        objectLocation: ObjectLocation,
        attribute: String,
        value: String
    ): GraphNotation =
        NotationReducer()
            .applyStructural(
                notation,
                UpsertAttributeCommand(
                    objectLocation, AttributeName(attribute), ScalarAttributeNotation(value)))
            .graphNotation


    // The Preview Worker's live count, read from its node's progress emit (the engine's in-tree analogue of the
    // trace path the JS Job UI polls). The migrated Preview is a fresh node with the SAME stable id, found here.
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
