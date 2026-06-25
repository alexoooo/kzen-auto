package tech.kzen.auto.server.objects.job

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.LogicExecution
import tech.kzen.lib.common.exec.logic.LogicExecutionFacade
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultPaused
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.server.exec.logic.context.MutableLogicControl
import tech.kzen.lib.server.exec.logic.context.MutableLogicResourceScope
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * Drives the state-migration path of [JobExecution]: pause -> edit config -> continue. The controller re-reads
 * the (possibly edited) notation each tick and passes it as the run's `graphDefinition`; when it changes while
 * paused, [JobExecution] rebuilds the Worker graph from the edit so the new config takes effect, migrating each
 * surviving Worker's run-scoped state by stable id via
 * [tech.kzen.auto.server.objects.job.worker.WorkerBase.captureMigrationState] /
 * [tech.kzen.auto.server.objects.job.worker.WorkerBase.loadMigrationState] (mirroring `ScriptExecution`'s
 * identity-continuity).
 *
 * [tech.kzen.auto.server.objects.job.worker.PreviewWorker] is the carry-forward testbed (it already keeps
 * header / rolling window / running count as run-scoped state). These tests STEP to a clean, in-flight-free
 * wavefront before migrating, so the carried Worker state is isolated from channel carryover (covered separately
 * by [JobMigrationCarryoverTest]): the first edit re-points the reader at an empty file so the rebuilt source
 * contributes nothing — with the migration the Preview keeps its accumulated count + header, without it the
 * rebuilt Preview would publish 0 / no header.
 */
class JobStateMigrationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/job-migration-preview-test.yaml")

    private val dir = Path.of("build/job-migration")
    private val input = dir.resolve("input.csv")
    private val empty = dir.resolve("empty.csv")

    private val mainLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
    private val readerLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/reader"))
    private val previewLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/preview"))

    private lateinit var context: KzenAutoContext
    private val runExecutionId = LogicRunExecutionId.random()


    //-----------------------------------------------------------------------------------------------------------------
    @Before
    fun setUp() {
        context = KzenAutoContext.forTest()

        Files.createDirectories(dir)
        Files.newBufferedWriter(input).use {
            it.write("id,name"); it.newLine()
            for (i in 0 until 20) {
                it.write("$i,n$i"); it.newLine()
            }
        }
        // Header only, no data rows: the rebuilt reader reaches EOF immediately and emits nothing.
        Files.newBufferedWriter(empty).use {
            it.write("id,name"); it.newLine()
        }
    }


    @After
    fun tearDown() {
        context.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun editingConfigWhilePausedRebuildsAndMigratesWorkerState() {
        val notation = AutoTestUtils.readNotation()
        val baseDefinition = definitionOf(notation)

        // A no-edit resume must NOT migrate: in production the controller rebuilds the definition from
        // notation each tick, so two independent builds of the SAME notation have to be objectDefinitions-equal
        // (the change signal JobExecution compares on), otherwise every resume would spuriously rebuild.
        assertEquals(
            baseDefinition.objectDefinitions,
            definitionOf(AutoTestUtils.readNotation()).objectDefinitions,
            "a fresh build of the same notation must be definition-equal (no spurious migration on resume)")

        // The edit: re-point the reader at the empty file, so the rebuilt source adds no rows.
        val editedDefinition = definitionOf(
            NotationReducer().applyStructural(
                notation,
                UpsertAttributeCommand(
                    readerLocation,
                    AttributeName("path"),
                    ScalarAttributeNotation(empty.toString().replace('\\', '/'))))
                .graphNotation)

        val execution = AutoTestUtils.liveLogicExecution(
            context, mainLocation, UnusedLogicHandle, runExecutionId)
        execution.beforeStart(TupleValue.empty)

        val control = MutableLogicControl(false)
        val resourceScope = MutableLogicResourceScope()

        // Settle to a parked wavefront, then step until the Preview has accumulated at least one batch (its
        // first progress publish is unthrottled, so the trace count is exact at the moment it first turns > 0).
        control.commandPause()
        var result: LogicResult = execution.continueOrStart(control, resourceScope, baseDefinition)
        assertIs<LogicResultPaused>(result)

        var guard = 0
        while (previewCount() == 0L && guard < 100) {
            assertIs<LogicResultPaused>(result)
            control.grantStepBudget(1)
            result = execution.continueOrStart(control, resourceScope, baseDefinition)
            guard += 1
        }
        assertIs<LogicResultPaused>(result)

        val pausedCount = previewCount()
        assertTrue(pausedCount > 0L, "Preview should accumulate at least one batch before the edit")
        assertEquals(listOf("id", "name"), previewHeader())

        // Resume against the edited definition: JobExecution rebuilds (reader now empty) and migrates the
        // Preview's state. The empty source adds nothing, so the run completes with the CARRIED count + header.
        control.commandUnpause()
        val finalResult = execution.continueOrStart(control, resourceScope, editedDefinition)
        assertIs<LogicResultSuccess>(finalResult)

        assertEquals(
            pausedCount, previewCount(),
            "migrated Preview carries its accumulated count across the edit (would be 0 without loadState)")
        assertEquals(
            listOf("id", "name"), previewHeader(),
            "migrated Preview carries its header across the edit (would be empty without loadState)")
    }


    @Test
    fun editingNonReaderConfigResumesReaderFromItsPosition() {
        // Editing a NON-reader attribute (here the Preview's sample size) leaves the reader's path/delimiter/
        // header unchanged, so on resume CsvReaderWorker ADOPTS its open reader and continues from its file
        // position instead of reopening and re-reading from the top. Proof: the Preview (which carries its
        // accumulated count forward) ends counting each row AT MOST ONCE — a total <= the file's row count. A
        // restart would instead re-read the whole file on top of the carried count, giving carried + rows > rows.
        val rows = 100
        Files.newBufferedWriter(input).use {
            it.write("id,name"); it.newLine()
            for (i in 0 until rows) {
                it.write("$i,n$i"); it.newLine()
            }
        }

        val notation = AutoTestUtils.readNotation()
        val baseDefinition = definitionOf(notation)
        val editedDefinition = definitionOf(
            NotationReducer().applyStructural(
                notation,
                UpsertAttributeCommand(
                    previewLocation, AttributeName("sample"), ScalarAttributeNotation("500")))
                .graphNotation)

        val execution = AutoTestUtils.liveLogicExecution(
            context, mainLocation, UnusedLogicHandle, runExecutionId)
        execution.beforeStart(TupleValue.empty)

        val control = MutableLogicControl(false)
        val resourceScope = MutableLogicResourceScope()

        // Step until the Preview has processed at least one batch (so a restart's carried count would be > 0,
        // making restart's total strictly exceed the row count).
        control.commandPause()
        var result: LogicResult = execution.continueOrStart(control, resourceScope, baseDefinition)
        assertIs<LogicResultPaused>(result)

        var guard = 0
        while (previewCount() == 0L && guard < 100) {
            assertIs<LogicResultPaused>(result)
            control.grantStepBudget(1)
            result = execution.continueOrStart(control, resourceScope, baseDefinition)
            guard += 1
        }
        assertIs<LogicResultPaused>(result)
        val pausedCount = previewCount()
        assertTrue(pausedCount > 0L, "Preview should accumulate at least one batch before the edit")

        control.commandUnpause()
        val finalResult = execution.continueOrStart(control, resourceScope, editedDefinition)
        assertIs<LogicResultSuccess>(finalResult)

        val finalCount = previewCount()
        assertEquals(
            rows.toLong(), finalCount,
            "reader resumes from position and the migration carries any in-flight batch, so every row is " +
                "counted exactly once (a restart would re-read the file and exceed $rows; a dropped in-flight " +
                "batch would fall short)")
        assertTrue(finalCount > pausedCount, "the run should make progress past the pause point")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun definitionOf(graphNotation: GraphNotation): GraphDefinition =
        AutoTestUtils.graphDefinitionAttempt(graphNotation)
            .transitiveSuccessful
            .filterTransitive(documentPath)


    private fun previewProgress(): Map<*, *>? {
        val snapshot = context.logicTraceStore.lookup(
            runExecutionId, LogicTraceQuery(LogicTracePath.root))
            ?: return null
        val progressPath = JobConventions.workerProgressPath(
            context.objectStableMapper.objectStableId(previewLocation))
        return snapshot.values[progressPath]?.value?.get() as? Map<*, *>
    }


    private fun previewCount(): Long =
        previewProgress()?.get("count") as? Long ?: 0L


    private fun previewHeader(): List<*> =
        previewProgress()?.get("header") as? List<*> ?: listOf<String>()


    //-----------------------------------------------------------------------------------------------------------------
    private object UnusedLogicHandle: LogicHandle {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation
        ): LogicExecutionFacade =
            error("nested logic should not start for a Job")
    }
}
