package tech.kzen.auto.server.service.impl

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * Integration coverage for [ServerLogicController]'s live-edit migration wiring (logic-spec §5): pause -> edit
 * config -> resume, driven through the PUBLIC control surface (start / pause / step / continueOrStart) exactly as
 * the client does. Proves the controller detects that the resumed-with snapshot differs from the running
 * definition, recompiles, and drives [tech.kzen.lib.server.exec.engine.RunEngine.migrate] at the quiescent
 * barrier — the production path on top of the migration mechanics proven in isolation by
 * [tech.kzen.auto.server.exec.job.JobMigrationTest].
 *
 * Uses the Job preview fixture (CsvReaderWorker -> channel -> PreviewWorker). Editing a NON-reader attribute (the
 * Preview's sample size) leaves the reader's config unchanged, so on migrate the reader RESUMES from its file
 * position and the Preview ADOPTS its carried count while the channel carries any in-flight batch — every row
 * counted exactly once, so the final count equals the row total. The step ticks before the edit pass the SAME
 * (base) snapshot, so they must NOT spuriously migrate (a fresh build of the same notation is definition-equal);
 * a spurious rebuild would restart the reader and overshoot the total — so the exact count doubly proves the
 * change-detection only fires on a real edit.
 */
class ServerLogicControllerMigrationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/job-migration-preview-test.yaml")
    private val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
    private val previewLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/preview"))

    private val dir = Path.of("build/job-migration")
    private val input = dir.resolve("input.csv")
    private val rows = 100

    private lateinit var context: KzenAutoContext


    //-----------------------------------------------------------------------------------------------------------------
    @Before
    fun setUp() {
        Files.createDirectories(dir)
        Files.newBufferedWriter(input).use { writer ->
            writer.write("id,name")
            writer.newLine()
            for (i in 0 until rows) {
                writer.write("$i,n$i")
                writer.newLine()
            }
        }
        context = KzenAutoContext.forTest()
    }


    @After
    fun tearDown() {
        context.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun editingJobConfigWhilePausedMigratesThroughController() {
        val controller = context.serverLogicController

        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation)
        val edited = AutoTestUtils.graphDefinitionAttempt(
            edit(baseNotation, previewLocation, "sample", "500"))

        val runId = controller.start(jobLocation, base)
            ?: fail("Unable to start run")

        // Launch paused at the entry wavefront, then step until the Preview has consumed at least one batch — the
        // reader's file position is then past the start, so resume-vs-restart is observable. Each step passes the
        // base snapshot (== baseline), so none of them migrate.
        controller.pause(runId)
        awaitState(LogicRunState.Paused)

        var guard = 0
        while (previewCount(runId) == 0L && guard < 300) {
            controller.step(runId, base)
            awaitState(LogicRunState.Paused)
            guard += 1
        }
        assertTrue(previewCount(runId) > 0L, "Preview should consume at least one batch before the edit")

        // The edit was fabricated OUT-OF-BAND (NotationReducer on a local notation copy — the graph store never
        // saw a command), so hand the controller the store notification production would have delivered: edit
        // detection is event-driven (the controller observes the graph store), and a release only reconciles
        // against the baseline once some notation event has landed.
        runBlocking { controller.onStoreRefresh(edited) }

        // Resume against the EDITED snapshot: the controller detects the change, recompiles, and migrates — the
        // reader resumes from its position, the Preview carries its count, the channel carries any in-flight batch.
        controller.continueOrStart(runId, edited)
        awaitDone()

        assertEquals(
            rows.toLong(), previewCount(runId),
            "edit-driven migrate is lossless through the controller: the reader resumed (no re-read overshoot) " +
                "and no in-flight batch was dropped, so every row is counted exactly once")
    }


    //-----------------------------------------------------------------------------------------------------------------
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


    private fun previewCount(runId: LogicRunId): Long {
        val snapshot = context.logicTrace.lookupRun(runId, LogicTraceQuery(LogicTracePath.root))
            ?: return 0L
        val progressPath = JobConventions.workerProgressPath(
            context.objectStableMapper.objectStableId(previewLocation))
        val progress = snapshot.values[progressPath]?.value?.get() as? Map<*, *>
            ?: return 0L
        return progress["count"] as? Long ?: 0L
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
