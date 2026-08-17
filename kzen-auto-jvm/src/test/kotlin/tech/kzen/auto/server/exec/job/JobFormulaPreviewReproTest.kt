package tech.kzen.auto.server.exec.job

import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.auto.server.util.awaitDone
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail


/**
 * Regression coverage for the JS Job UI's live-progress read path, driven through the PUBLIC
 * [tech.kzen.auto.server.service.impl.ServerLogicController] surface exactly as the client does (start ->
 * continueOrStart). Reproduces the user's Job-1.yaml (a scalar FormulaSourceWorker -> PreviewWorker with BLANK
 * ports, so the order-driven synthesis wires both the one-way channel and the Preview's external serve).
 *
 * The bug this guards: a Job's Workers are each hosted as their OWN engine node (registering their own stable id
 * in the trace history), but the Job ROOT (`main`) only HOSTS them and emits no trace event of its own — unlike a
 * Script / Flow root, whose per-element emits self-register it. So `mostRecent(main)` returned
 * null and [tech.kzen.auto.client.objects.document.job.JobProgressStore.fetchRunProgress] bailed on its very
 * first line, hiding ALL live worker progress (an empty Preview) even though the progress was correctly in the
 * trace. (The engine-served view resolves this structurally now — the run's root node exists from engine
 * construction, so mostRecent(main) resolves it before any emit.) The assertions mirror that read path:
 * mostRecent(main) must resolve the run, and lookupRun(runId) at the
 * Preview's [JobConventions.workerProgressPath] must carry the live sample.
 */
class JobFormulaPreviewReproTest {
    private val documentPath = DocumentPath.parse("test/job/report/job-formula-preview-repro-test.yaml")
    private val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
    private val previewLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/Preview"))

    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    @Test
    fun scalarJobPreviewIsDiscoverableAndCarriesItsSample() {
        context = KzenAutoContext.forTest()
        val controller = context.serverLogicController

        val attempt = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())

        val runId = controller.start(jobLocation, attempt)
            ?: fail("Unable to start run")
        controller.continueOrStart(runId, attempt)
        controller.awaitDone()

        // JobProgressStore.fetchRunProgress FIRST resolves the run via mostRecent(main); a null here is the bug
        // (the Preview never even fetches its worker progress).
        val recent = context.logicTrace.mostRecent(jobLocation)
        assertNotNull(recent, "the JS resolves the run via mostRecent(main); null => the Preview never fetches")
        assertEquals(runId, recent.logicRunId, "mostRecent resolves to THIS run")

        // Then it reads the Preview's live sample from the whole-run snapshot at the worker-progress path.
        val snapshot = context.logicTrace.lookupRun(recent.logicRunId, LogicTraceQuery(LogicTracePath.root))
            ?: fail("run snapshot not found")
        val progressPath = JobConventions.workerProgressPath(
            context.objectStableMapper.objectStableId(previewLocation))
        val progress = snapshot.values[progressPath]?.value?.get() as? Map<*, *>
            ?: fail("Preview progress not in the run snapshot")

        assertEquals(100L, progress["count"], "the Preview counted every emitted scalar element")
        assertEquals(100, (progress["rows"] as? List<*>)?.size, "the Preview sample carries the emitted rows")
    }
}
