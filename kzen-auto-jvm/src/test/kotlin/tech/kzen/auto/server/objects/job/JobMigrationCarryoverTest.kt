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
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * Reproduces the reported message loss: free-run a buffered Job pipeline, pause it MID-STREAM (so the channel
 * holds the buffered + parked-mid-send batches the fast reader produced ahead of the Preview), then resume
 * against an edited (non-reader) definition. Because [tech.kzen.auto.server.objects.job.worker.CsvReaderWorker]
 * resumes from its file position rather than re-reading, any batch dropped at the rebuild would be lost for
 * good and the Preview's total would fall short of the row count. [JobExecution] carries each Channel's
 * in-flight payloads across the rebuild ([tech.kzen.auto.server.objects.job.channel.JobChannel.drainBuffered] /
 * [tech.kzen.auto.server.objects.job.channel.JobChannel.preload]), so the migration is lossless: every row is
 * counted exactly once.
 *
 * Unlike [JobStateMigrationTest] (which steps to a clean, in-flight-free wavefront to isolate worker-state
 * migration), this pauses a FREE run, so the cut deliberately lands with batches still in flight.
 */
class JobMigrationCarryoverTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/job-migration-carryover-test.yaml")

    private val dir = Path.of("build/job-migration")
    private val input = dir.resolve("carryover.csv")

    private val mainLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
    private val previewLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/preview"))

    private lateinit var context: KzenAutoContext
    private val runExecutionId = LogicRunExecutionId.random()


    //-----------------------------------------------------------------------------------------------------------------
    @Before
    fun setUp() {
        context = KzenAutoContext.forTest()
        Files.createDirectories(dir)
    }


    @After
    fun tearDown() {
        context.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun migrationCarriesInFlightChannelBatchesAcrossAPausedEdit() {
        // Large enough that the free run lasts well past the controller's pause-poll interval, so the pause
        // reliably lands mid-stream rather than after completion.
        val rows = 200_000
        Files.newBufferedWriter(input).use {
            it.write("id,name"); it.newLine()
            for (i in 0 until rows) {
                it.write("$i,n$i"); it.newLine()
            }
        }

        val notation = AutoTestUtils.readNotation()
        val baseDefinition = definitionOf(notation)
        // A non-reader edit (Preview's sample size) leaves the reader's config unchanged, so on resume the
        // reader continues from its file position — making any dropped in-flight batch a permanent loss.
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

        // Free-run on a background thread (continueOrStart blocks until it pauses), then pause once the Preview
        // is well underway so the cut lands mid-stream with batches still in the channel.
        val firstResult = AtomicReference<LogicResult>()
        val runThread = thread(name = "carryover-free-run") {
            firstResult.set(execution.continueOrStart(control, resourceScope, baseDefinition))
        }

        val deadline = System.nanoTime() + 20_000_000_000L
        while (previewCount() < 2_000L && System.nanoTime() < deadline) {
            Thread.sleep(1)
        }
        control.commandPause()
        runThread.join(20_000)

        val pausedCount = previewCount()
        assertIs<LogicResultPaused>(firstResult.get())
        assertTrue(
            pausedCount in 2_000L until rows.toLong(),
            "expected a mid-stream pause (so batches are in flight), but Preview counted $pausedCount of $rows")

        // Resume against the edited definition: JobExecution rebuilds, carrying each channel's in-flight batches
        // into the new graph; the reader resumes from its position. No batch may be dropped or replayed.
        control.commandUnpause()
        val finalResult = execution.continueOrStart(control, resourceScope, editedDefinition)
        assertIs<LogicResultSuccess>(finalResult)

        assertEquals(
            rows.toLong(), previewCount(),
            "every row must be counted exactly once across the paused edit — no in-flight channel batch dropped")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun definitionOf(graphNotation: GraphNotation): GraphDefinition =
        AutoTestUtils.graphDefinitionAttempt(graphNotation)
            .transitiveSuccessful
            .filterTransitive(documentPath)


    private fun previewCount(): Long {
        val snapshot = context.logicTraceStore.lookup(
            runExecutionId, LogicTraceQuery(LogicTracePath.root))
            ?: return 0L
        val progressPath = JobConventions.workerProgressPath(
            context.objectStableMapper.objectStableId(previewLocation))
        val progress = snapshot.values[progressPath]?.value?.get() as? Map<*, *>
            ?: return 0L
        return progress["count"] as? Long ?: 0L
    }


    //-----------------------------------------------------------------------------------------------------------------
    private object UnusedLogicHandle: LogicHandle {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation
        ): LogicExecutionFacade =
            error("nested logic should not start for a Job")
    }
}
